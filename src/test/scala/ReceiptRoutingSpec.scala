import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpEntity, Multipart}
import akka.http.scaladsl.model.headers.{HttpChallenge, HttpCredentials}
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives, AuthenticationResult}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Source
import akka.util.ByteString
import de.choffmeister.auth.akkahttp.Authenticator
import model.{ErrorResponse, ReceiptEntity, User}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, FlatSpec}

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.concurrent.Future

class ReceiptRoutingSpec extends FlatSpec with Matchers with ScalatestRouteTest with MockitoSugar with ScalaFutures with JsonProtocols  {

  val receiptService = mock[ReceiptService]
  val fileService = mock[FileService]

  it should "create receipt from file upload" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User("123-user", "name", "hash")))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(receiptService, fileService, authentication)

    val receipt = ReceiptEntity(userId = "123-user")
    when(fileService.save(any[Source[ByteString, Any]])).thenReturn(Future("1234"))
    when(receiptService.createReceipt("123-user", "1234")).thenReturn(Future(receipt))

    val content = "file content".getBytes
    val multipartForm =
      Multipart.FormData(Multipart.FormData.BodyPart.Strict(
        "receipt",
        HttpEntity(`application/octet-stream`, content),
        Map("filename" -> "receipt.png")))

    Post("/user/123-user/receipt", multipartForm) ~> receiptRouting.routes ~> check {
      status shouldBe Created
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity] shouldBe receipt
    }
  }

  it should "reject receipt from file upload if form field is not present" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User("123-user", "name", "hash")))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(receiptService, fileService, authentication)

    val receipt = ReceiptEntity(userId = "123-user")
    when(fileService.save(any[Source[ByteString, Any]])).thenReturn(Future("1234"))
    when(receiptService.createReceipt("123-user", "1234")).thenReturn(Future(receipt))

    val content = "file content".getBytes
    val multipartForm =
      Multipart.FormData(Multipart.FormData.BodyPart.Strict(
        "random_field",
        HttpEntity(`application/octet-stream`, content),
        Map("filename" -> "receipt.png")))

    Post("/user/123-user/receipt", multipartForm) ~> receiptRouting.routes ~> check {
      status shouldBe BadRequest
      contentType shouldBe `application/json`
      responseAs[ErrorResponse] shouldEqual ErrorResponse("Request is missing required form field 'receipt'")
    }
  }

  it should "add a file to existing receipt" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User("123-user", "name", "hash")))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(receiptService, fileService, authentication)

    val receipt = ReceiptEntity(userId = "123-user")
    when(fileService.save(any[Source[ByteString, Any]])).thenReturn(Future("1234"))
    when(receiptService.createReceipt("123-user", "1234")).thenReturn(Future(receipt))

    val content = "file content".getBytes
    val multipartForm =
      Multipart.FormData(Multipart.FormData.BodyPart.Strict(
        "receipt",
        HttpEntity(`application/octet-stream`, content),
        Map("filename" -> "receipt.png")))

    Post("/user/123-user/receipt/1234/file", multipartForm) ~> receiptRouting.routes ~> check {
      status shouldBe Created
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity].id shouldBe receipt.id
    }
  }

}
