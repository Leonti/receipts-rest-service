import java.io.ByteArrayInputStream
import java.util.zip.{ZipEntry, ZipInputStream}

import ReceiptTestUtils._
import UserTestUtils._
import TestConfig._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import authentication.OAuth2AccessTokenResponse
import model.ReceiptEntity
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import akka.stream.scaladsl.Sink
import akka.util.ByteString

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BackupSpec extends FlatSpec with Matchers with ScalaFutures {
  implicit val defaultPatience =
    PatienceConfig(timeout = Span(60, Seconds), interval = Span(1000, Millis))
  implicit val system       = ActorSystem()
  implicit val materializer = ActorMaterializer()

  it should "test downloading a backup" in {

    val zipEntriesFuture: Future[List[ZipEntry]] = for {
      (userInfo, accessToken)      <- createUser()
      requestEntity <- createImageFileContent()
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"$appHostPort/receipt",
          entity = requestEntity,
          headers = List(Authorization(OAuth2BearerToken(accessToken.value)))
        ))
      firstReceiptEntity <- Unmarshal(response.entity).to[ReceiptEntity]
      receiptEntity      <- getProcessedReceipt(firstReceiptEntity.id, accessToken.value)
      backupToken <- Http()
        .singleRequest(
          HttpRequest(
            method = HttpMethods.GET,
            uri = s"$appHostPort/backup/token",
            headers = List(Authorization(OAuth2BearerToken(accessToken.value)))
          ))
        .flatMap(response => Unmarshal(response.entity).to[OAuth2AccessTokenResponse])
      backupResponse <- Http().singleRequest(
        HttpRequest(method = HttpMethods.GET,
                    uri = s"$appHostPort/user/${userInfo.id}/backup/download?access_token=${backupToken.accessToken}",
                    entity = requestEntity))
      backupBytes <- responseToBytes(backupResponse)
      zipEntries  <- Future.successful(toZipEntries(backupBytes))
    } yield zipEntries

    whenReady(zipEntriesFuture) { zipEntries: List[ZipEntry] =>
      zipEntries.length shouldBe 2
    }

  }

  def responseToBytes(httpResponse: HttpResponse): Future[Array[Byte]] = {
    val reduceSink = Sink.reduce[ByteString](_ ++ _)
    val byteString = httpResponse.entity.getDataBytes().runWith(reduceSink, materializer)
    byteString.map(_.toArray)
  }

  def toZipEntries(bytes: Array[Byte]): List[ZipEntry] = {
    val zipStream  = new ZipInputStream(new ByteArrayInputStream(bytes))
    val zipEntries = Stream.continually(zipStream.getNextEntry).takeWhile(_ != null).toList
    zipStream.close()

    zipEntries
  }

}
