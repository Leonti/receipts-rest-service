import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart}
import akka.http.scaladsl.model.headers.{HttpChallenge, HttpCredentials}
import akka.http.scaladsl.server.directives.{AuthenticationResult, SecurityDirectives}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import model._
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import java.io.{ByteArrayInputStream, File}

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import routing.ReceiptRouting
import service.{FileService, ReceiptService}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.testkit._
import processing.ReceiptFiles

class ReceiptRoutingSpec extends FlatSpec with Matchers with ScalatestRouteTest with MockitoSugar with JsonProtocols {

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(5).second.dilated(system))

  val receiptService = mock[ReceiptService]
  val receiptFiles   = mock[ReceiptFiles]
  val fileService    = mock[FileService]

  def utf8TextEntity(content: String) = {
    val bytes = ByteString(content)
    HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, bytes)
  }

  it should "create receipt from file upload" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", passwordHash = "hash")))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(receiptService, fileService, receiptFiles, authentication)

    val receipt = ReceiptEntity(userId = "123-user")
    val pendingFile = PendingFile(
      id = "1",
      userId = "1",
      receiptId = "1"
    )
    when(receiptFiles.submitFile(any[String], any[String], any[File], any[String])).thenReturn(Future.successful(pendingFile))
    when(receiptService.createReceipt("123-user", Some(BigDecimal("12.38")), "some description", 1480130712396l, List("veggies", "food")))
      .thenReturn(Future(receipt))
    when(receiptService.findById(receipt.id)).thenReturn(Future(Some(receipt)))

    val content = "file content".getBytes
    val multipartForm =
      Multipart.FormData(
        Multipart.FormData.BodyPart.Strict("receipt", HttpEntity(`application/octet-stream`, content), Map("filename" -> "receipt.png")),
        Multipart.FormData.BodyPart.Strict("total", utf8TextEntity("12.38")),
        Multipart.FormData.BodyPart.Strict("description", utf8TextEntity("some description")),
        Multipart.FormData.BodyPart.Strict("transactionTime", utf8TextEntity("1480130712396")),
        Multipart.FormData.BodyPart.Strict("tags", utf8TextEntity("veggies,food"))
      )

    Post("/user/123-user/receipt", multipartForm) ~> receiptRouting.routes ~> check {
      println("RESPONSE" + responseAs[String])
      status shouldBe Created
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity] shouldBe receipt
    }
  }

  it should "reject receipt from file upload if form field is not present" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", passwordHash = "hash")))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(receiptService, fileService, receiptFiles, authentication)

    val receipt = ReceiptEntity(userId = "123-user")
    val pendingFile = PendingFile(
      id = "1",
      userId = "1",
      receiptId = "1"
    )
    when(receiptFiles.submitFile(any[String], any[String], any[File], any[String])).thenReturn(Future.successful(pendingFile))
    when(receiptService.createReceipt("123-user", Some(BigDecimal("12.38")), "some description", 1480130712396l, List("veggies", "food")))
      .thenReturn(Future(receipt))

    val content = "file content".getBytes
    val multipartForm =
      Multipart.FormData(
        Multipart.FormData.BodyPart.Strict("random_field",
                                           HttpEntity(`application/octet-stream`, content),
                                           Map("filename" -> "receipt.png")),
        Multipart.FormData.BodyPart.Strict("total", utf8TextEntity("12.38")),
        Multipart.FormData.BodyPart.Strict("description", utf8TextEntity("some description")),
        Multipart.FormData.BodyPart.Strict("transactionTime", utf8TextEntity("1480130712396")),
        Multipart.FormData.BodyPart.Strict("tags", utf8TextEntity("veggies,food"))
      )

    Post("/user/123-user/receipt", multipartForm) ~> receiptRouting.routes ~> check {
      status shouldBe BadRequest
      contentType shouldBe `application/json`
      responseAs[ErrorResponse] shouldEqual ErrorResponse("Request is missing required form field 'receipt'")
    }
  }

  it should "add a file to existing receipt" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", passwordHash = "hash")))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(receiptService, fileService, receiptFiles, authentication)

    val receipt = ReceiptEntity(userId = "123-user")
    val pendingFile = PendingFile(
      id = "1",
      userId = "1",
      receiptId = "1"
    )
    when(receiptFiles.submitFile(any[String], any[String], any[File], any[String])).thenReturn(Future.successful(pendingFile))
    when(receiptService.findById(receipt.id)).thenReturn(Future(Some(receipt)))

    val content = "file content".getBytes
    val multipartForm =
      Multipart.FormData(
        Multipart.FormData.BodyPart.Strict("receipt", HttpEntity(`application/octet-stream`, content), Map("filename" -> "receipt.png")))

    Post(s"/user/123-user/receipt/${receipt.id}/file", multipartForm) ~> receiptRouting.routes ~> check {
      status shouldBe Created
      contentType shouldBe `application/json`
      responseAs[PendingFile] shouldBe pendingFile
    }
  }

  it should "return a file content" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", passwordHash = "hash")))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(receiptService, fileService, receiptFiles, authentication)

    val fileEntity = FileEntity(id = "1", parentId = None, ext = "txt", metaData = GenericMetadata(fileType = "TXT", length = 11))
    val receipt    = ReceiptEntity(userId = "123-user", files = List(fileEntity))

    val source = StreamConverters.fromInputStream(() => new ByteArrayInputStream("some text".getBytes))

    when(receiptService.findById(receipt.id)).thenReturn(Future(Some(receipt)))
    when(fileService.fetch("123-user", fileEntity.id)).thenReturn(source)

    Get(s"/user/123-user/receipt/${receipt.id}/file/${fileEntity.id}.txt") ~> receiptRouting.routes ~> check {
      status shouldBe OK
      contentType shouldBe `text/plain(UTF-8)`
      responseAs[String] should include("some text")
    }
  }

  it should "read receipt by id" in {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", passwordHash = "hash")))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(receiptService, fileService, receiptFiles, authentication)

    val receipt = ReceiptEntity(userId = "123-user", files = List(), description = "some description")
    when(receiptService.findById(receipt.id)).thenReturn(Future(Some(receipt)))

    Get(s"/user/123-user/receipt/${receipt.id}") ~> receiptRouting.routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity].description shouldBe "some description"
    }
  }

  it should "patch a receipt" in {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", passwordHash = "hash")))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(receiptService, fileService, receiptFiles, authentication)

    val receipt = ReceiptEntity(userId = "123-user", files = List(), description = "some description")
    when(receiptService.findById(receipt.id)).thenReturn(Future(Some(receipt)))

    val patchedReceipt = receipt.copy(description = "some new description", total = Some(BigDecimal("12.38")))
    when(receiptService.save(patchedReceipt)).thenReturn(Future(patchedReceipt))

    val patch = """[
                  |  {
                  |    "op": "replace",
                  |    "path": "/description",
                  |    "value": "some new description"
                  |  },
                  |  {
                  |    "op": "replace",
                  |    "path": "/total",
                  |    "value": 12.38
                  |  }
                  |]""".stripMargin

    Patch(s"/user/123-user/receipt/${receipt.id}", HttpEntity(`application/json`, patch)) ~> receiptRouting.routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity].description shouldBe "some new description"
      responseAs[ReceiptEntity].total shouldBe Some(BigDecimal("12.38"))
    }
  }

  it should "unset total after patch with null" in {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", passwordHash = "hash")))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(receiptService, fileService, receiptFiles, authentication)

    val receipt = ReceiptEntity(userId = "123-user", files = List(), description = "some description", total = Some(BigDecimal("12.38")))
    when(receiptService.findById(receipt.id)).thenReturn(Future(Some(receipt)))

    val patchedReceipt = receipt.copy(total = None)
    when(receiptService.save(patchedReceipt)).thenReturn(Future(patchedReceipt))

    val patch = """[
                  |  {
                  |    "op": "replace",
                  |    "path": "/total",
                  |    "value": null
                  |  }
                  |]""".stripMargin

    Patch(s"/user/123-user/receipt/${receipt.id}", HttpEntity(`application/json`, patch)) ~> receiptRouting.routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity].total shouldBe None
    }
  }

  it should "unset total after patch with remove" in {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", passwordHash = "hash")))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(receiptService, fileService, receiptFiles, authentication)

    val receipt = ReceiptEntity(userId = "123-user", files = List(), description = "some description", total = Some(BigDecimal("12.38")))
    when(receiptService.findById(receipt.id)).thenReturn(Future(Some(receipt)))

    val patchedReceipt = receipt.copy(total = None)
    when(receiptService.save(patchedReceipt)).thenReturn(Future(patchedReceipt))

    val patch = """[
                  |  {
                  |    "op": "remove",
                  |    "path": "/total"
                  |  }
                  |]""".stripMargin

    Patch(s"/user/123-user/receipt/${receipt.id}", HttpEntity(`application/json`, patch)) ~> receiptRouting.routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity].total shouldBe None
    }
  }

  it should "set tags with a patch" in {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", passwordHash = "hash")))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(receiptService, fileService, receiptFiles, authentication)

    val receipt = ReceiptEntity(userId = "123-user", files = List(), description = "some description", total = Some(BigDecimal("12.38")))
    when(receiptService.findById(receipt.id)).thenReturn(Future(Some(receipt)))

    val patchedTags    = List("vegetables", "food")
    val patchedReceipt = receipt.copy(tags = patchedTags)
    when(receiptService.save(patchedReceipt)).thenReturn(Future(patchedReceipt))

    val patch = """[
                  |  {
                  |    "op": "replace",
                  |    "path": "/tags",
                  |    "value": ["vegetables", "food"]
                  |  }
                  |]""".stripMargin

    Patch(s"/user/123-user/receipt/${receipt.id}", HttpEntity(`application/json`, patch)) ~> receiptRouting.routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity].tags shouldBe patchedTags
    }
  }

  it should "delete a receipt " in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", passwordHash = "hash")))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(receiptService, fileService, receiptFiles, authentication)

    val fileEntity = FileEntity(id = "1", parentId = None, ext = "txt", metaData = GenericMetadata(fileType = "TXT", length = 11))
    val receipt    = ReceiptEntity(userId = "123-user", files = List(fileEntity))

    when(receiptService.findById(receipt.id)).thenReturn(Future(Some(receipt)))
    when(fileService.delete("123-user", fileEntity.id)).thenReturn(Future {})
    when(receiptService.delete(receipt.id)).thenReturn(Future {})

    Delete(s"/user/123-user/receipt/${receipt.id}") ~> receiptRouting.routes ~> check {
      println(responseAs[String])
      status shouldBe OK
    }
  }

}
