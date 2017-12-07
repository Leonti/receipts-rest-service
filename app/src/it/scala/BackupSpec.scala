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
import de.choffmeister.auth.common.OAuth2AccessTokenResponse
import model.{CreateUserRequest, JsonProtocols, ReceiptEntity}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.scaladsl.Sink
import akka.util.ByteString

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class BackupSpec extends FlatSpec with Matchers with ScalaFutures with JsonProtocols {
  implicit val defaultPatience =
    PatienceConfig(timeout = Span(60, Seconds), interval = Span(500, Millis))
  implicit val system       = ActorSystem()
  implicit val materializer = ActorMaterializer()

  it should "test downloading a backup" in {
    val username          = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val zipEntriesFuture: Future[List[ZipEntry]] = for {
      userInfo      <- createUser(createUserRequest)
      accessToken   <- authenticateUser(userInfo)
      requestEntity <- createImageFileContent()
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"$appHostPort/user/${userInfo.id}/receipt",
          entity = requestEntity,
          headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))
        ))
      firstReceiptEntity <- Unmarshal(response.entity).to[ReceiptEntity]
      receiptEntity      <- getProcessedReceipt(userInfo.id, firstReceiptEntity.id, accessToken.accessToken)
      backupToken <- Http()
        .singleRequest(
          HttpRequest(
            method = HttpMethods.GET,
            uri = s"$appHostPort/user/${userInfo.id}/backup/token",
            headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))
          ))
        .flatMap(response => Unmarshal(response.entity).to[OAuth2AccessTokenResponse])
      backupResponse: HttpResponse <- Http().singleRequest(
        HttpRequest(method = HttpMethods.GET,
                    uri = s"$appHostPort/user/${userInfo.id}/backup/download?access_token=${backupToken.accessToken}",
                    entity = requestEntity))
      backupBytes <- responseToBytes(backupResponse)
      zipEntries  <- Future.successful(toZipEntries(backupBytes))
    } yield zipEntries

    whenReady(zipEntriesFuture) { (zipEntries: List[ZipEntry]) =>
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
