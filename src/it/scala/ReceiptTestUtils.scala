import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import model.{JsonProtocols, PendingFile, ReceiptEntity}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, _}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.util.ByteString

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.BufferedSource

package object ReceiptTestUtils extends JsonProtocols {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  // utility functions
  def getProcessedReceipt(userId: String, receiptId: String, accessToken: String): Future[ReceiptEntity] = {

    def pendingFiles(): Future[List[PendingFile]] = {
      for {
        userPendingFilesResponse <- Http().singleRequest(HttpRequest(method = HttpMethods.GET,
          uri = s"http://localhost:9000/user/$userId/pending-file",
          headers = List(Authorization(OAuth2BearerToken(accessToken)))))
        userPendingFiles <- Unmarshal(userPendingFilesResponse.entity).to[List[PendingFile]]
      } yield userPendingFiles
    }

    def receiptEntity(): Future[ReceiptEntity] = {
      for {
        response <- Http().singleRequest(HttpRequest(method = HttpMethods.GET,
          uri = s"http://localhost:9000/user/$userId/receipt/$receiptId",
          headers = List(Authorization(OAuth2BearerToken(accessToken)))))
        receipt <- Unmarshal(response.entity).to[ReceiptEntity]
      } yield receipt
    }

    def pendingFilesToReceipt(pendingFilesFuture: Future[List[PendingFile]], retry: Int = 0): Future[ReceiptEntity] = {
      val checkInterval = 1.seconds

      if (retry > 40) Future.failed(new RuntimeException("Could not get receipt entity in time")) else {
        for {
          pending <- pendingFilesFuture
          receipt <- if (pending.exists(_.receiptId == receiptId))
            akka.pattern.after(checkInterval, using = system.scheduler)(pendingFilesToReceipt(pendingFiles(), retry + 1))
          else
            receiptEntity()
        } yield receipt
      }
    }

    pendingFilesToReceipt(pendingFiles())
  }

  def createTextFileContent(text: String): Future[RequestEntity] = {
    val content = text.getBytes
    val multipartForm =
      Multipart.FormData(Multipart.FormData.BodyPart.Strict(
        "receipt",
        HttpEntity(`application/octet-stream`, content),
        Map("filename" -> "receipt.txt")),
        Multipart.FormData.BodyPart.Strict("total", utf8TextEntity("12.38")),
        Multipart.FormData.BodyPart.Strict("description", utf8TextEntity("some description"))
      )
    Marshal(multipartForm).to[RequestEntity]
  }

  def createImageFileContent(): Future[RequestEntity] = {
    val receiptImage: BufferedSource = scala.io.Source.fromURL(getClass.getResource("/receipt.png"), "ISO-8859-1")
    val content = receiptImage.map(_.toByte).toArray
    val multipartForm =
      Multipart.FormData(Multipart.FormData.BodyPart.Strict(
        "receipt",
        HttpEntity(`application/octet-stream`, content),
        Map("filename" -> "receipt.png")),
        Multipart.FormData.BodyPart.Strict("total", utf8TextEntity("12.38")),
        Multipart.FormData.BodyPart.Strict("description", utf8TextEntity("some description"))
      )
    Marshal(multipartForm).to[RequestEntity]
  }

  def utf8TextEntity(content: String) = {
    val bytes = ByteString(content)
    HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, bytes)
  }

}
