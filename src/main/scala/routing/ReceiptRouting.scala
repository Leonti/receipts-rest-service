package routing

import akka.http.scaladsl.model.{MediaTypes, HttpEntity}
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.directives.{AuthenticationDirective, FileInfo}
import akka.http.scaladsl.server.{AuthorizationFailedRejection, MissingFormFieldRejection, RejectionHandler}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import model._
import service.{FileService, ReceiptService}

import akka.actor.ActorSystem
import akka.event.{LoggingAdapter, Logging}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

class ReceiptRouting(receiptService: ReceiptService, fileService: FileService, authenticaton: AuthenticationDirective[User]
                    )(implicit val executor: ExecutionContextExecutor) extends JsonProtocols {

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

  val routes =
    handleRejections(myRejectionHandler) {
      pathPrefix("user" / Segment) { userId: String =>
        authenticaton { user =>
          authorize(user.id == userId) {
            pathPrefix("receipt") {
              get {
                extractRequest { request =>

                  val userReceiptsFuture = receiptService.findForUserId(userId)

                  onComplete(userReceiptsFuture) { userReceipts =>
                    complete(userReceipts)
                  }
                }
              } ~
                path(Segment / "file") { receiptId: String =>
                  post {
                    fileUpload("receipt") {
                      case (metadata: FileInfo, byteSource: Source[ByteString, Any]) =>
                        val fileUploadFuture: Future[FileEntity] = fileService.save(userId, byteSource, ext(metadata.fileName))

                        val receiptFuture: Future[Option[ReceiptEntity]] = fileUploadFuture.flatMap((file: FileEntity) =>
                          receiptService.addFileToReceipt(receiptId, file))

                        onComplete(receiptFuture) { (result: Try[Option[ReceiptEntity]]) =>

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
                post { //curl -X POST -H 'Content-Type: application/octet-stream' -d @test.txt http://localhost:9000/leonti/receipt
                  fileUpload("receipt") {
                    case (metadata: FileInfo, byteSource: Source[ByteString, Any]) =>
                      val fileUploadFuture: Future[FileEntity] = fileService.save(userId, byteSource, ext(metadata.fileName))

                      val receiptIdFuture: Future[ReceiptEntity] = fileUploadFuture.flatMap((file: FileEntity) => receiptService.createReceipt(
                        userId = userId, file = file
                      ))

                      onComplete(receiptIdFuture) { receipt =>
                        complete(Created -> receipt)
                      }
                  }
                }
            } ~
            pathPrefix("file"/ Segment) { fileId =>
              get {
                val fileSource: Source[ByteString, Unit] = fileService.fetch(userId, fileId)
                complete(HttpResponse(entity = HttpEntity.Chunked.fromData(MediaTypes.`application/octet-stream`, fileSource)))
              }
            }
          }
        }
      }
    }

}
