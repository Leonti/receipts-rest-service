import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart}
import akka.http.scaladsl.model.headers.{HttpChallenge, HttpCredentials}
import akka.http.scaladsl.server.directives.{AuthenticationResult, SecurityDirectives}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.util.ByteString
import model._
import org.scalatest.{FlatSpec, Matchers}
import akka.actor.ActorSystem
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import routing.ReceiptRouting
import TestInterpreters._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import akka.testkit._
import service.ReceiptPrograms
import cats.implicits._
import org.scalatest.concurrent.ScalaFutures

class ReceiptRoutingSpec extends FlatSpec with Matchers with ScalaFutures with ScalatestRouteTest {

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(5).second.dilated(system))

  def utf8TextEntity(content: String) = {
    val bytes = ByteString(content)
    HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, bytes)
  }

  val receiptInt = new ReceiptInterpreterTagless(List(), List())
  val fileInt = new FileInterpreterTagless()
  val randomInt = new RandomInterpreterTagless("", 0)
  val ocrInt = new OcrInterpreterTagless()

  it should "create receipt from file upload" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", externalIds = List())))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(new ReceiptPrograms(receiptInt, fileInt, randomInt, ocrInt), authentication)

    val receipt = ReceiptEntity(
      id = "",
      userId = "123-user",
      total = Some(BigDecimal(12.38)),
      description = "some description",
      timestamp = 0,
      lastModified = 0,
      transactionTime = 1480130712396l,
      tags = List("veggies", "food"),
      files = List()
    )

    val content = "file content".getBytes
    val multipartForm =
      Multipart.FormData(
        Multipart.FormData.BodyPart.Strict("receipt", HttpEntity(`application/octet-stream`, content), Map("filename" -> "receipt.png")),
        Multipart.FormData.BodyPart.Strict("total", utf8TextEntity("12.38")),
        Multipart.FormData.BodyPart.Strict("description", utf8TextEntity("some description")),
        Multipart.FormData.BodyPart.Strict("transactionTime", utf8TextEntity("1480130712396")),
        Multipart.FormData.BodyPart.Strict("tags", utf8TextEntity("veggies,food"))
      )

    Post("/user/123-user/receipt", multipartForm) ~> receiptRouting.routes("testLocation") ~> check {
      status shouldBe Created
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity] shouldBe receipt
    }
  }

  it should "reject receipt if file already exists" in {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", externalIds = List())))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    val receipt = ReceiptEntity(
      id = "",
      userId = "123-user",
      total = Some(BigDecimal(12.38)),
      description = "some description",
      timestamp = 0,
      lastModified = 0,
      transactionTime = 1480130712396l,
      tags = List("veggies", "food"),
      files = List()
    )

    val receiptRouting = new ReceiptRouting(new ReceiptPrograms(receiptInt, new FileInterpreterTagless(md5Response = List(StoredFile("123-user", "fileId", "md5", 42))), randomInt, ocrInt), authentication)

    val content = "file content".getBytes
    val multipartForm =
      Multipart.FormData(
        Multipart.FormData.BodyPart.Strict("receipt", HttpEntity(`application/octet-stream`, content), Map("filename" -> "receipt.png")),
        Multipart.FormData.BodyPart.Strict("total", utf8TextEntity("12.38")),
        Multipart.FormData.BodyPart.Strict("description", utf8TextEntity("some description")),
        Multipart.FormData.BodyPart.Strict("transactionTime", utf8TextEntity("1480130712396")),
        Multipart.FormData.BodyPart.Strict("tags", utf8TextEntity("veggies,food"))
      )

    Post("/user/123-user/receipt", multipartForm) ~> receiptRouting.routes("testLocation") ~> check {
      status shouldBe BadRequest
      contentType shouldBe `application/json`
      val errorResponse = responseAs[ErrorResponse]
      errorResponse.error should include("same file")
    }
  }

  it should "reject receipt from file upload if form field is not present" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", externalIds = List())))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    val receiptRouting = new ReceiptRouting(new ReceiptPrograms(receiptInt, fileInt, randomInt, ocrInt), authentication)

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

    Post("/user/123-user/receipt", multipartForm) ~> receiptRouting.routes("testLocation") ~> check {
      status shouldBe BadRequest
      contentType shouldBe `application/json`
      responseAs[ErrorResponse] shouldEqual ErrorResponse("Request is missing required form field 'receipt'")
    }
  }

  it should "add a file to existing receipt" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", externalIds = List())))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    val receipt = ReceiptEntity(id = "1", userId = "123-user")
    val receiptRouting = new ReceiptRouting(new ReceiptPrograms(
      new ReceiptInterpreterTagless(List(receipt), List()), fileInt,
      new RandomInterpreterTagless("2", 0),
      ocrInt), authentication)

    val pendingFile = PendingFile(
      id = "2",
      userId = "123-user",
      receiptId = "1"
    )

    val content = "file content".getBytes
    val multipartForm =
      Multipart.FormData(
        Multipart.FormData.BodyPart.Strict("receipt", HttpEntity(`application/octet-stream`, content), Map("filename" -> "receipt.png")))

    Post(s"/user/123-user/receipt/${receipt.id}/file", multipartForm) ~> receiptRouting.routes("testLocation") ~> check {
      status shouldBe Created
      contentType shouldBe `application/json`
      responseAs[PendingFile] shouldBe pendingFile
    }
  }

  it should "not allow to add a receipt with existing file" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", externalIds = List())))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    val receipt = ReceiptEntity(id = "1", userId = "123-user")
    val receiptRouting = new ReceiptRouting(new ReceiptPrograms(
      new ReceiptInterpreterTagless(receipts = List(receipt), ocrs = List()),
      new FileInterpreterTagless(md5Response = List(StoredFile("123-user", "fileId", "md5", 42))),
      new RandomInterpreterTagless("2", 0),
      ocrInt), authentication)

    val pendingFile = PendingFile(
      id = "2",
      userId = "123-user",
      receiptId = "1"
    )

    val content = "file content".getBytes
    val multipartForm =
      Multipart.FormData(
        Multipart.FormData.BodyPart.Strict("receipt", HttpEntity(`application/octet-stream`, content), Map("filename" -> "receipt.png")))

    Post(s"/user/123-user/receipt/${receipt.id}/file", multipartForm) ~> receiptRouting.routes("testLocation") ~> check {
      status shouldBe BadRequest
      contentType shouldBe `application/json`
      val errorResponse = responseAs[ErrorResponse]
      errorResponse.error should include("same file")
    }
  }

  it should "return a file content" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", externalIds = List())))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    val fileEntity =
      FileEntity(id = "1", parentId = None, ext = "txt", md5 = None, metaData = GenericMetadata(fileType = "TXT", length = 11))
    val receipt = ReceiptEntity(id = "2", userId = "123-user", files = List(fileEntity))

    val receiptRouting = new ReceiptRouting(new ReceiptPrograms(
      new ReceiptInterpreterTagless(List(receipt), List()),
      fileInt, randomInt, ocrInt), authentication)

    Get(s"/user/123-user/receipt/${receipt.id}/file/${fileEntity.id}.txt") ~> receiptRouting.routes("testLocation") ~> check {
      status shouldBe OK
      contentType shouldBe `text/plain(UTF-8)`
      whenReady(responseEntity.toStrict(1.second).map { _.data }.map(_.utf8String)) { stringResponse =>
        stringResponse should include("some text")
      }
    }
  }

  it should "read receipt by id" in {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", externalIds = List())))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    val receipt = ReceiptEntity(id = "2", userId = "123-user", files = List(), description = "some description")
    val receiptRouting = new ReceiptRouting(new ReceiptPrograms(
      new ReceiptInterpreterTagless(List(receipt), List()),
      fileInt, randomInt, ocrInt), authentication)

    Get(s"/user/123-user/receipt/${receipt.id}") ~> receiptRouting.routes("testLocation") ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity].description shouldBe "some description"
    }
  }

  it should "patch a receipt" in {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", externalIds = List())))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    val receipt = ReceiptEntity(id = "1", userId = "123-user", files = List(), description = "some description")
    val receiptRouting = new ReceiptRouting(new ReceiptPrograms(
      new ReceiptInterpreterTagless(List(receipt), List()),
      fileInt, randomInt, ocrInt), authentication)

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

    Patch(s"/user/123-user/receipt/${receipt.id}", HttpEntity(`application/json`, patch)) ~> receiptRouting.routes("testLocation") ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity].description shouldBe "some new description"
      responseAs[ReceiptEntity].total shouldBe Some(BigDecimal("12.38"))
    }
  }

  it should "unset total after patch with null" in {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", externalIds = List())))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    val receipt = ReceiptEntity(userId = "123-user", files = List(), description = "some description", total = Some(BigDecimal("12.38")))
    val receiptRouting = new ReceiptRouting(new ReceiptPrograms(
      new ReceiptInterpreterTagless(List(receipt), List()),
      fileInt, randomInt, ocrInt), authentication)

    val patch = """[
                  |  {
                  |    "op": "replace",
                  |    "path": "/total",
                  |    "value": null
                  |  }
                  |]""".stripMargin

    Patch(s"/user/123-user/receipt/${receipt.id}", HttpEntity(`application/json`, patch)) ~> receiptRouting.routes("testLocation") ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity].total shouldBe None
    }
  }

  it should "unset total after patch with remove" in {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", externalIds = List())))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    val receipt = ReceiptEntity(userId = "123-user", files = List(), description = "some description", total = Some(BigDecimal("12.38")))
    val receiptRouting = new ReceiptRouting(new ReceiptPrograms(
      new ReceiptInterpreterTagless(List(receipt), List()),
      fileInt, randomInt, ocrInt), authentication)

    val patch = """[
                  |  {
                  |    "op": "remove",
                  |    "path": "/total"
                  |  }
                  |]""".stripMargin

    Patch(s"/user/123-user/receipt/${receipt.id}", HttpEntity(`application/json`, patch)) ~> receiptRouting.routes("testLocation") ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity].total shouldBe None
    }
  }

  it should "set tags with a patch" in {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", externalIds = List())))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    val receipt = ReceiptEntity(userId = "123-user", files = List(), description = "some description", total = Some(BigDecimal("12.38")))
    val receiptRouting = new ReceiptRouting(new ReceiptPrograms(
      new ReceiptInterpreterTagless(List(receipt), List()),
      fileInt, randomInt, ocrInt), authentication)

    val patch = """[
                  |  {
                  |    "op": "replace",
                  |    "path": "/tags",
                  |    "value": ["vegetables", "food"]
                  |  }
                  |]""".stripMargin

    Patch(s"/user/123-user/receipt/${receipt.id}", HttpEntity(`application/json`, patch)) ~> receiptRouting.routes("testLocation") ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity].tags shouldBe List("vegetables", "food")
    }
  }

  it should "delete a receipt " in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "123-user", userName = "name", externalIds = List())))
    }
    val authentication = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    val fileEntity =
      FileEntity(id = "1", parentId = None, ext = "txt", md5 = None, metaData = GenericMetadata(fileType = "TXT", length = 11))
    val receipt = ReceiptEntity(id = "2", userId = "123-user", files = List(fileEntity))
    val receiptRouting = new ReceiptRouting(new ReceiptPrograms(
      new ReceiptInterpreterTagless(List(receipt), List()),
      fileInt, randomInt, ocrInt), authentication)

    Delete(s"/user/123-user/receipt/${receipt.id}") ~> receiptRouting.routes("testLocation") ~> check {
      status shouldBe OK
    }
  }

}
