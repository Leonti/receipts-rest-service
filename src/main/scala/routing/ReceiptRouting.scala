package routing

import java.io.File

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, RequestContext, Route, RouteResult}
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.directives.{AuthenticationDirective, ContentTypeResolver, FileInfo}
import akka.http.scaladsl.server.{AuthorizationFailedRejection, MissingFormFieldRejection, RejectionHandler, Route}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import model._
import service.{FileService, ReceiptService}
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.ActorMaterializer
import spray.json._
import gnieh.diffson._

class ReceiptRouting(receiptService: ReceiptService, fileService: FileService, authenticaton: AuthenticationDirective[User]
                    )(implicit val executor: ExecutionContextExecutor) extends JsonProtocols {


  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  def myRejectionHandler =
    RejectionHandler.newBuilder()
      .handle { case MissingFormFieldRejection(field) =>
        complete(BadRequest -> ErrorResponse(s"Request is missing required form field '${field}'"))
      }
      .handle { case AuthorizationFailedRejection =>
        complete((Forbidden -> ErrorResponse("Access forbidden")))
      }
      .result()

  def ext(fileName: String): String = fileName.split("\\.")(1)

  val respondWithReceipt: (Future[Option[ReceiptEntity]]) => Route = (receiptFuture) => {

    onComplete(receiptFuture) { result: Try[Option[ReceiptEntity]] =>
      result match {
        case Success(receiptResult: Option[ReceiptEntity]) => receiptResult match {
          case Some(receipt) => complete(OK -> receipt)
          case None => complete(BadRequest -> ErrorResponse(s"Failed to get or update receipt"))
        }
        case Failure(t: Throwable) => complete(InternalServerError -> ErrorResponse(s"server failure: ${t}"))
      }
    }
  }

  val patchAndSaveReceipt: (String, String) => Future[Option[ReceiptEntity]] = (receiptId, jsonPatch) => {
    val patchedReceipt: Future[Option[ReceiptEntity]] = receiptService.findById(receiptId)
      .map(_.map(patchReceipt(_, jsonPatch)))

    patchedReceipt.flatMap(_ match {
      case Some(receipt) => receiptService.save(receipt).map(Some(_))
      case None => Future.successful(None)
    })
  }

  val patchReceipt: (ReceiptEntity, String) => ReceiptEntity = (receiptEntity, jsonPatch) => {

    val asJson: String = receiptEntity.toJson.compactPrint
    val patched: String = JsonPatch.parse(jsonPatch).apply(asJson)
    patched.parseJson.convertTo[ReceiptEntity]
  }

  val routes =
    handleRejections(myRejectionHandler) {
      pathPrefix("user" / Segment / "receipt") { userId: String =>
        authenticaton { user =>
          authorize(user.id == userId) {
            path(Segment) { receiptId: String =>
              get {
                respondWithReceipt(receiptService.findById(receiptId))
              } ~
              patch {
                entity(as[String]) { receiptPatch =>
                  respondWithReceipt(patchAndSaveReceipt(receiptId, receiptPatch))
                }
              }
            } ~
            path(Segment / "file") { receiptId: String =>
              post {
                uploadedFile("receipt") {
                  case (metadata: FileInfo, file: File) =>
                    val fileUploadFuture: Future[FileEntity] = fileService.save(userId, file, ext(metadata.fileName))

                    val receiptFuture: Future[Option[ReceiptEntity]] = fileUploadFuture.flatMap((file: FileEntity) =>
                      receiptService.addFileToReceipt(receiptId, file))

                    onComplete(receiptFuture) { (result: Try[Option[ReceiptEntity]]) =>

                      file.delete()
                      result match {
                        case Success(receiptResult: Option[ReceiptEntity]) => receiptResult match {
                          case Some(receipt) => complete(Created -> receipt)
                          case None => complete(BadRequest -> ErrorResponse(s"Receipt ${receiptId} doesn't exist"))
                        }
                        case Failure(t: Throwable) => complete(InternalServerError -> ErrorResponse(s"server failure: ${t}"))
                      }
                    }
                }
              }
            } ~
            path(Segment / "file" / Segment) { (receiptId, fileIdWithExt) =>
              get {
                val fileId = fileIdWithExt.split('.')(0)

                val extFuture: Future[Option[String]] = receiptService.findById(receiptId)
                  .map(receiptEntity => receiptEntity
                    .flatMap(_.files.find(_.id == fileId).map(_.ext)))

                onComplete(extFuture) { (extResult: Try[Option[String]]) =>

                  extResult match {
                    case Success(extOption: Option[String]) => extOption match {
                      case Some(ext) =>
                        val fileSource = fileService.fetch(userId, fileId)
                        val contentType = ContentTypeResolver.Default("file." + ext)

                        complete(HttpResponse(entity = HttpEntity(
                          contentType, fileSource)))
                      case None => complete(BadRequest -> ErrorResponse(s"File ${fileId} was not found in receipt ${receiptId}"))
                    }
                    case Failure(t: Throwable) => complete(InternalServerError -> ErrorResponse(s"server failure: ${t}"))
                  }
                }

              }
            } ~
            get {
              val userReceiptsFuture = receiptService.findForUserId(userId)

              onComplete(userReceiptsFuture) { userReceipts =>
                complete(userReceipts)
              }
            } ~
            post { //curl -X POST -H 'Content-Type: application/octet-stream' -d @test.txt http://localhost:9000/leonti/receipt
              FileUploadDirective.uploadedFileWithFields("receipt", "total", "description") {
                (parsedForm: ParsedForm) =>

                    import akka.stream.scaladsl.FileIO
                    val uploadedFile = parsedForm.files("receipt")
                    val byteSource = FileIO.fromFile(uploadedFile.file)
                    // byteSource: Source[ByteString, Any]
                    val fileUploadFuture: Future[FileEntity] = fileService.save(userId, uploadedFile.file, ext(uploadedFile.fileInfo.fileName))

                    val receiptIdFuture: Future[ReceiptEntity] = fileUploadFuture.flatMap((file: FileEntity) => receiptService.createReceipt(
                      userId = userId,
                      file = file,
                      total = Try(BigDecimal(parsedForm.fields("total"))).map(Some(_)).getOrElse(None),
                      description = parsedForm.fields("description")
                    ))

                    onComplete(receiptIdFuture) { receipt =>
                      uploadedFile.file.delete()
                      complete(Created -> receipt)
                    }
                }
            }
          }
        }
      }
    }

}
