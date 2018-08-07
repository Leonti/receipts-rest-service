package routing

import java.io.{File, PrintWriter, StringWriter}

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.directives.{AuthenticationDirective, ContentTypeResolver, FileInfo}
import akka.http.scaladsl.server._
import model._
import service.{ReceiptErrors, ReceiptPrograms}
import akka.actor.ActorSystem

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import cats.implicits._

class ReceiptRouting(
    receiptPrograms: ReceiptPrograms[Future],
    authenticaton: AuthenticationDirective[User]
)(implicit system: ActorSystem, executor: ExecutionContextExecutor, materializer: ActorMaterializer)
    extends JsonProtocols {

  val logger = Logger(LoggerFactory.getLogger("ReceiptRouting"))

  def myRejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case MissingFormFieldRejection(field) =>
          complete(BadRequest -> ErrorResponse(s"Request is missing required form field '$field'"))
      }
      .handle {
        case AuthorizationFailedRejection =>
          complete(Forbidden -> ErrorResponse("Access forbidden"))
      }
      .result()

  val respondWithReceipt: (Future[Option[ReceiptEntity]]) => Route = (receiptFuture) => {

    onComplete(receiptFuture) { result: Try[Option[ReceiptEntity]] =>
      result match {
        case Success(receiptResult: Option[ReceiptEntity]) =>
          receiptResult match {
            case Some(receipt) => complete(OK         -> receipt)
            case None          => complete(BadRequest -> ErrorResponse(s"Failed to get or update receipt"))
          }
        case Failure(t: Throwable) => complete(InternalServerError -> ErrorResponse(s"server failure: ${t}"))
      }
    }
  }

  val deleteReceipt: String => Route = (receiptId) => {

    val deletionFuture: Future[Option[Unit]] = receiptPrograms.removeReceipt(receiptId)

    onComplete(deletionFuture) {
      case Success(deletionOption: Option[Unit]) =>
        deletionOption match {
          case Some(_) => complete(OK)
          case None    => complete(NotFound -> ErrorResponse(s"Receipt $receiptId was not found"))
        }
      case Failure(t) => complete(InternalServerError -> ErrorResponse(s"server failure: $t"))
    }
  }

  val receiptServiceError: ReceiptErrors.Error => Route = {
    case ReceiptErrors.FileAlreadyExists() =>
      complete(BadRequest -> ErrorResponse("Can't create receipt with the same file"))
    case ReceiptErrors.ReceiptNotFound(receiptId: String) =>
      complete(BadRequest -> ErrorResponse(s"Receipt $receiptId doesn't exist"))
  }

  val toTempFile: FileInfo => File = fileInfo => File.createTempFile(fileInfo.fileName, ".tmp")

  def routes(uploadsLocation: String): Route =
    handleRejections(myRejectionHandler) {
      pathPrefix("user" / Segment / "receipt") { userId: String =>
        authenticaton { user =>
          authorize(user.id == userId) {
            path(Segment) { receiptId: String =>
              get {
                respondWithReceipt(receiptPrograms.findById(receiptId))
              } ~
              patch {
                entity(as[String]) { receiptPatch =>
                  val receiptFuture = receiptPrograms.patchReceipt(receiptId, receiptPatch)
                  respondWithReceipt(receiptFuture)
                }
              } ~
              delete {
                deleteReceipt(receiptId)
              }
            } ~
            path(Segment / "file") { receiptId: String =>
              post {
                storeUploadedFile("receipt", toTempFile) {
                  case (metadata: FileInfo, file: File) =>
                    val pendingFilesFuture: Future[Either[ReceiptErrors.Error, PendingFile]] =
                      receiptPrograms.addUploadedFileToReceipt(uploadsLocation, userId, receiptId, metadata, file)

                    onComplete(pendingFilesFuture) {
                      case Success(pendingFileEither: Either[ReceiptErrors.Error, PendingFile]) =>
                        pendingFileEither match {
                          case Right(pendingFile: PendingFile) => complete(Created -> pendingFile)
                          case Left(error)                     => receiptServiceError(error)
                        }
                      case Failure(t: Throwable) => complete(InternalServerError -> ErrorResponse(s"server failure: $t"))
                    }
                }
              }
            } ~
            path(Segment / "file" / Segment) { (receiptId, fileIdWithExt) =>
              get {
                val fileId = fileIdWithExt.split('.')(0)

                val fileToServeFuture = receiptPrograms
                  .receiptFileWithExtension(receiptId, fileId)

                onComplete(fileToServeFuture) {
                  case Success(fileToServeOption: Option[FileToServe]) =>
                    fileToServeOption match {
                      case Some(fileToServe) =>
                        val contentType = ContentTypeResolver.Default("file." + fileToServe.ext)
                        complete(HttpResponse(entity = HttpEntity(contentType, fileToServe.source)))
                      case None => complete(BadRequest -> ErrorResponse(s"File $fileId was not found in receipt $receiptId"))
                    }
                  case Failure(t: Throwable) => complete(InternalServerError -> ErrorResponse(s"server failure: $t"))
                }
              }
            } ~
            get {
              parameters("last-modified".as[Long].?, "q".as[String].?) { (lastModified: Option[Long], queryOption: Option[String]) =>
                val userReceiptsFuture: Future[Seq[ReceiptEntity]] =
                  receiptPrograms
                    .findForUser(userId, lastModified, queryOption)

                onComplete(userReceiptsFuture) { userReceipts =>
                  complete(userReceipts)
                }
              }
            } ~
            post { //curl -X POST -H 'Content-Type: application/octet-stream' -d @test.txt http://localhost:9000/leonti/receipt
              FileUploadDirective.uploadedFileWithFields("receipt", "total", "description", "transactionTime", "tags") {
                parsedForm: ParsedForm =>
                  val receiptFuture = receiptPrograms
                    .createReceipt(uploadsLocation, userId, parsedForm)

                  onComplete(receiptFuture) {
                    case Success(receiptEither: Either[ReceiptErrors.Error, ReceiptEntity]) =>
                      receiptEither match {
                        case Right(receipt: ReceiptEntity) => complete(Created -> receipt)
                        case Left(error)                   => receiptServiceError(error)
                      }
                    case Failure(error) =>
                      // do proper logging here
                      val sw = new StringWriter
                      error.printStackTrace(new PrintWriter(sw))
                      logger.error(sw.toString)
                      complete(InternalServerError -> ErrorResponse(s"Could not create a receipt"))
                  }
              }
            }
          }
        }
      }
    }

}
