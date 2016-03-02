import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.Segment
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.scaladsl.Source
import akka.util.ByteString
import de.choffmeister.auth.akkahttp.Authenticator
import model.{User, ErrorResponse, ReceiptEntity}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

class ReceiptRouting(receiptService: ReceiptService, fileService: FileService, authenticator: Authenticator[User]
                    )(implicit val executor: ExecutionContextExecutor) extends JsonProtocols {

  val routes = pathPrefix("user" / Segment) { userId: String =>
    authenticator.bearerToken(acceptExpired = true) { user =>
      authorize(user.id == userId) {
        pathPrefix("receipt") {
          get {
            extractRequest { request =>

              val userReceiptsFuture = receiptService.findForUserId(userId)
              complete {
                userReceiptsFuture.map[ToResponseMarshallable](receipts => receipts)
              }
            }
          } ~
            post { //curl -X POST -H 'Content-Type: application/octet-stream' -d @test.txt http://localhost:9000/leonti/receipt
              fileUpload("receipt") {
                case (metadata: FileInfo, byteSource: Source[ByteString, Any]) =>
                  val fileUploadFuture: Future[String] = fileService.save(byteSource)

                  val receiptIdFuture: Future[ReceiptEntity] = fileUploadFuture.flatMap((fileId: String) => receiptService.createReceipt(
                    userId = userId, fileId = fileId
                  ))

                  onComplete(receiptIdFuture) { receipt =>
                    complete(Created -> receipt)
                  }
              }
            } ~
            path(Segment / "file") { receiptId: String =>
              post {
                println("adding file to receipt")
                fileUpload("receipt") {
                  case (metadata: FileInfo, byteSource: Source[ByteString, Any]) =>
                    val fileUploadFuture: Future[String] = fileService.save(byteSource)

                    val receiptFuture: Future[Option[ReceiptEntity]] = fileUploadFuture.flatMap((fileId: String) => receiptService.addFileToReceipt(receiptId, fileId))

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
            }
        }
      }
    }
  }

}
