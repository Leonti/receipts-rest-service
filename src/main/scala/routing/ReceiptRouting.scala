package routing

import java.io.{File, PrintWriter, Serializable, StringWriter}

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.directives.{AuthenticationDirective, ContentTypeResolver, FileInfo}
import akka.http.scaladsl.server._
import model._
import service.{FileService, ReceiptService}
import akka.actor.ActorSystem

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.Logger
import spray.json._
import gnieh.diffson._
import org.slf4j.LoggerFactory
import processing.ReceiptFiles

class ReceiptRouting(
                      receiptService: ReceiptService,
                      fileService: FileService,
                      receiptFiles: ReceiptFiles,
                      authenticaton: AuthenticationDirective[User]
                    )(implicit system: ActorSystem, executor: ExecutionContextExecutor, materializer: ActorMaterializer) extends JsonProtocols {

  val logger = Logger(LoggerFactory.getLogger("ReceiptRouting"))

  def myRejectionHandler =
    RejectionHandler.newBuilder()
      .handle { case MissingFormFieldRejection(field) =>
        complete(BadRequest -> ErrorResponse(s"Request is missing required form field '${field}'"))
      }
      .handle { case AuthorizationFailedRejection =>
        complete(Forbidden -> ErrorResponse("Access forbidden"))
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

    patchedReceipt.flatMap({
      case Some(receipt) => receiptService.save(receipt).map(Some(_))
      case None => Future.successful(None)
    })
  }

  val patchReceipt: (ReceiptEntity, String) => ReceiptEntity = (receiptEntity, jsonPatch) => {
    val asJson: String = receiptEntity.copy(total =
      if (receiptEntity.total.isDefined) receiptEntity.total else Some(BigDecimal("0")))
      .toJson.compactPrint
    val patched: String = JsonPatch.parse(jsonPatch).apply(asJson)
    patched.parseJson.convertTo[ReceiptEntity]
  }

  val deleteReceipt: (String) => Route = (receiptId) => {

    val deletion: Future[Option[Future[Unit]]] = receiptService.findById(receiptId).map(_.map({ receipt =>
      val fileFutures = receipt.files.map({ fileEntity =>
        println(s"Calling fileService with ${receipt.userId} ${fileEntity.id}")
        fileService.delete(receipt.userId, fileEntity.id)
      })

      Future.sequence(fileFutures).flatMap(_ => receiptService.delete(receiptId))
    }))

    onComplete(deletion) {
        case Success(deletionOption: Option[Future[Unit]]) => deletionOption match {
          case Some(deletionFuture: Future[Unit]) => onComplete(deletionFuture) { (deletionResult: Try[Unit]) =>
            deletionResult match {
              case Success(_) => complete(OK)
              case Failure(t) => complete(InternalServerError -> ErrorResponse(s"server failure: $t"))
            }
          }
          case None => complete(NotFound -> ErrorResponse(s"Receipt $receiptId was not found"))
        }
        case Failure(t) => complete(InternalServerError -> ErrorResponse(s"server failure: $t"))
    }
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
              } ~
              delete {
                deleteReceipt(receiptId)
              }
            } ~
            path(Segment / "file") { receiptId: String =>
              post {
                uploadedFile("receipt") {
                  case (metadata: FileInfo, file: File) =>

                    val pendingFilesFuture = receiptService.findById(receiptId).flatMap({
                      case Some(receiptEntity: ReceiptEntity) => receiptFiles
                        .submitFile(userId, receiptEntity.id, file, ext(metadata.fileName))
                        .map(pendingFile => Some(pendingFile))
                      case None => Future.successful(None)
                    })

                    onComplete(pendingFilesFuture) { (result: Try[Option[PendingFile]]) =>

                      result match {
                        case Success(maybePendingFile: Option[PendingFile]) => maybePendingFile match {
                          case Some(pendingFile) => complete(Created -> pendingFile)
                          case None => complete(BadRequest -> ErrorResponse(s"Receipt $receiptId doesn't exist"))
                        }
                        case Failure(t: Throwable) => complete(InternalServerError -> ErrorResponse(s"server failure: $t"))
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

                        println(s"contentType $contentType")

                        complete(HttpResponse(entity = HttpEntity(
                          contentType, fileSource)))
                      case None => complete(BadRequest -> ErrorResponse(s"File $fileId was not found in receipt $receiptId"))
                    }
                    case Failure(t: Throwable) => complete(InternalServerError -> ErrorResponse(s"server failure: $t"))
                  }
                }

              }
            } ~
            get {
              parameters("last-modified".as[Long].?) { (lastModified: Option[Long]) =>
                val userReceiptsFuture = receiptService.findForUserId(userId, lastModified)

                onComplete(userReceiptsFuture) { userReceipts =>
                  complete(userReceipts)
                }
              }
            } ~
            post { //curl -X POST -H 'Content-Type: application/octet-stream' -d @test.txt http://localhost:9000/leonti/receipt
              FileUploadDirective.uploadedFileWithFields("receipt", "total", "description", "transactionTime", "tags") {
                (parsedForm: ParsedForm) =>

                  val uploadedFile = parsedForm.files("receipt")
                  val start = System.currentTimeMillis()
                  logger.info(s"Received file to upload ${uploadedFile.file.getAbsolutePath}")

                  val receiptFuture: Future[ReceiptEntity] = for {
                    receipt <- receiptService.createReceipt(
                      userId = userId,
                      total = Try(BigDecimal(parsedForm.fields("total"))).map(Some(_)).getOrElse(None),
                      description = parsedForm.fields("description"),
                      transactionTime = parsedForm.fields("transactionTime").toLong,
                      tags = parsedForm.fields("tags").split(",").toList
                    )
                    pendingFile <- receiptFiles.submitFile(userId, receipt.id, uploadedFile.file, ext(uploadedFile.fileInfo.fileName))
                  } yield receipt

                  onComplete(receiptFuture) { receiptTry =>
                    val end = System.currentTimeMillis()
                    logger.info(s"Receipt created in ${(end-start)/1000}s")

                    receiptTry match {
                      case Success(receipt: ReceiptEntity) => complete(Created -> receipt)
                      case Failure(error) => {

                        // do proper logging here
                        val sw = new StringWriter
                        error.printStackTrace(new PrintWriter(sw))
                        println(sw.toString)

                        complete(InternalServerError -> ErrorResponse(s"Could not create a receipt"))
                      }
                    }
                  }
                }
            }
          }
        }
      }
    }

}
