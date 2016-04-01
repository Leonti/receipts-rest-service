import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpEntity, Multipart}
import akka.http.scaladsl.model.headers.{HttpChallenge, HttpCredentials}
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives, AuthenticationResult}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Source
import akka.util.ByteString
import de.choffmeister.auth.akkahttp.Authenticator
import model._
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, FlatSpec}
import java.io.File

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import routing.ReceiptRouting
import service.{FileService, ReceiptService}

import scala.concurrent.Future

class ReceiptRoutingSpec extends FlatSpec with Matchers with ScalatestRouteTest with MockitoSugar with JsonProtocols  {

  val receiptService = mock[ReceiptService]
  val fileService = mock[FileService]

  it should "create receipt from file upload" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User("123-user", "name", "hash")))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(receiptService, fileService, authentication)

    val receipt = ReceiptEntity(userId = "123-user")
    val fileEntity = FileEntity(id = "1", ext = "png", metaData = GenericMetadata(fileType = "TXT", length = 11))
    when(fileService.save(any[String], any[File], any[String])).thenReturn(Future(fileEntity))
    when(receiptService.createReceipt("123-user", fileEntity)).thenReturn(Future(receipt))

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
    val fileEntity = FileEntity(id = "1", ext = "png", metaData = GenericMetadata(fileType = "TXT", length = 11))
    when(fileService.save(any[String], any[File], any[String])).thenReturn(Future(fileEntity))
    when(receiptService.createReceipt("123-user", fileEntity)).thenReturn(Future(receipt))

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
    val fileEntity = FileEntity(id = "1", ext = "png", metaData = GenericMetadata(fileType = "TXT", length = 11))
    when(fileService.save(any[String], any[File], any[String])).thenReturn(Future(fileEntity))
    when(receiptService.addFileToReceipt(receipt.id, fileEntity)).thenReturn(Future(Some(receipt)))

    val content = "file content".getBytes
    val multipartForm =
      Multipart.FormData(Multipart.FormData.BodyPart.Strict(
        "receipt",
        HttpEntity(`application/octet-stream`, content),
        Map("filename" -> "receipt.png")))

    Post(s"/user/123-user/receipt/${receipt.id}/file", multipartForm) ~> receiptRouting.routes ~> check {
      status shouldBe Created
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity].id shouldBe receipt.id
    }
  }

  it should "return a file content" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User("123-user", "name", "hash")))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(receiptService, fileService, authentication)

    val fileEntity = FileEntity(id = "1", ext = "txt", metaData = GenericMetadata(fileType = "TXT", length = 11))
    val receipt = ReceiptEntity(userId = "123-user", files = List(fileEntity))

    val bb = ByteString.newBuilder
    bb.putBytes("some text".getBytes)
    val source: Source[ByteString, Unit] = Source[ByteString](List(bb.result()))

    when(receiptService.findById(receipt.id)).thenReturn(Future(Some(receipt)))
    when(fileService.fetch("123-user", fileEntity.id)).thenReturn(source)

    Get(s"/user/123-user/receipt/${receipt.id}/file/${fileEntity.id}") ~> receiptRouting.routes ~> check {
      status shouldBe OK
      contentType shouldBe `text/plain(UTF-8)`
      responseAs[String] should include("some text")
    }
  }

  it should "read receipt by id" in {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User("123-user", "name", "hash")))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(receiptService, fileService, authentication)

    val receipt = ReceiptEntity(userId = "123-user", files = List(), description="some description")
    when(receiptService.findById(receipt.id)).thenReturn(Future(Some(receipt)))

    Get(s"/user/123-user/receipt/${receipt.id}") ~> receiptRouting.routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity].description shouldBe "some description"
    }
  }

  it should "patch a receipt" in {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User("123-user", "name", "hash")))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(receiptService, fileService, authentication)

    val receipt = ReceiptEntity(userId = "123-user", files = List(), description="some description")
    when(receiptService.findById(receipt.id)).thenReturn(Future(Some(receipt)))

    val patchedReceipt = receipt.copy(description = "some new description")
    when(receiptService.save(patchedReceipt)).thenReturn(Future(patchedReceipt))

    val patch = """[
                |  {
                |    "op": "replace",
                |    "path": "/description",
                |    "value": "some new description"
                |  }
                |]""".stripMargin

    Patch(s"/user/123-user/receipt/${receipt.id}", HttpEntity(`application/json`, patch)) ~> receiptRouting.routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity].description shouldBe "some new description"
    }
  }

}
