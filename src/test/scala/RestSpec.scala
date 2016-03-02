
import java.nio.ByteOrder

import akka.event.NoLogging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.headers.{`WWW-Authenticate`, BasicHttpCredentials, HttpChallenge, HttpCredentials}
import akka.http.scaladsl.model.{Multipart, HttpEntity, HttpResponse, HttpRequest}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.directives.{AuthenticationResult, AuthenticationDirective}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.{Source, Flow}
import akka.util.{ByteStringBuilder, ByteString}
import de.choffmeister.auth.akkahttp.Authenticator
import model._
import org.scalatest._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.mockito.Matchers.any
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import de.choffmeister.auth.common._
import scala.concurrent.duration._

import akka.http.scaladsl.server.{Route, Directive1}
import akka.http.scaladsl.server.directives.RouteDirectives._
import akka.http.scaladsl.server.directives._



class RestSpec extends FlatSpec with Matchers with ScalatestRouteTest with MockitoSugar with ScalaFutures with Service {

  override def testConfigSource = "akka.loglevel = WARNING"
  override def config = testConfig
  override val logger = NoLogging

  val userService = mock[UserService]

  override val userRouting = new UserRouting(userService)

  val receiptService = mock[ReceiptService]
  val fileService = mock[FileService]
  val authenticator = mock[Authenticator[User]]

  override val receiptRouting = new ReceiptRouting(receiptService, fileService, authenticator)
  override val authenticationRouting = new AuthenticationRouting(authenticator)

  it should "create a user" in {

    val createUserRequest = CreateUserRequest(userName = "userName", password = "password")
    val user = User(userName = "userName", passwordHash = "hash")
    when(userService.createUser(createUserRequest)).thenReturn(Future(Right(user)))

    Post("/user/create", createUserRequest) ~> routes ~> check {
      status shouldBe Created
      contentType shouldBe `application/json`
      responseAs[UserInfo] shouldBe UserInfo(user)
    }
  }

  it should "respond with appropriate error if user already exist" in {

    val createUserRequest = CreateUserRequest(userName = "userName", password = "password")
    when(userService.createUser(CreateUserRequest(userName = "userName", password = "password")))
      .thenReturn(Future(Left("User already exist")))

    Post("/user/create", createUserRequest) ~> routes ~> check {
      status shouldBe Conflict
      contentType shouldBe `application/json`
      responseAs[String] should include ("error creating user")
    }
  }

  it should "respond with InternalServerError on failure" in {

    val createUserRequest = CreateUserRequest(userName = "userName", password = "password")
    when(userService.createUser(createUserRequest)).thenReturn(Future.failed(new RuntimeException("test exception")))

    Post("/user/create", createUserRequest) ~> routes ~> check {
      status shouldBe InternalServerError
      contentType shouldBe `application/json`
      responseAs[String] should include ("server failure")
    }
  }

  "Service" should "return list of receipts" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User("123-user", "name", "hash")))
    }
    val authenticationDirective: AuthenticationDirective[User] = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    when(authenticator.bearerToken(true)).thenReturn(authenticationDirective)

    val receipts = List(ReceiptEntity(userId = "123-user"))
    when(receiptService.findForUserId("123-user")).thenReturn(Future(receipts))

    Get(s"/user/123-user/receipt") ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[List[ReceiptEntity]] shouldBe receipts
    }
  }

  "Service" should "not authorize wrong user" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User("123-user", "name", "hash")))
    }
    val authenticationDirective: AuthenticationDirective[User] = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    when(authenticator.bearerToken(true)).thenReturn(authenticationDirective)

    val receipts = List(ReceiptEntity(userId = "123-user"))
    when(receiptService.findForUserId("123-user")).thenReturn(Future(receipts))

    Get(s"/user/leonti/receipt") ~> routes ~> check {
      status shouldBe Forbidden
      contentType shouldBe `application/json`
      responseAs[ErrorResponse] shouldBe ErrorResponse("Access forbidden")
    }
  }

  it should "create receipt from file upload" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User("123-user", "name", "hash")))
    }
    val authenticationDirective: AuthenticationDirective[User] = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    when(authenticator.bearerToken(true)).thenReturn(authenticationDirective)

    val receipt = ReceiptEntity(userId = "123-user")
    when(fileService.save(any[Source[ByteString, Any]])).thenReturn(Future("1234"))
    when(receiptService.createReceipt("123-user", "1234")).thenReturn(Future(receipt))

    val content = "file content".getBytes
    val multipartForm =
      Multipart.FormData(Multipart.FormData.BodyPart.Strict(
        "receipt",
        HttpEntity(`application/octet-stream`, content),
        Map("filename" -> "receipt.png")))

    Post("/user/123-user/receipt", multipartForm) ~> routes ~> check {
      status shouldBe Created
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity] shouldBe receipt
    }
  }

  it should "reject receipt from file upload if form field is not present" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User("123-user", "name", "hash")))
    }
    val authenticationDirective: AuthenticationDirective[User] = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    when(authenticator.bearerToken(true)).thenReturn(authenticationDirective)

    val receipt = ReceiptEntity(userId = "123-user")
    when(fileService.save(any[Source[ByteString, Any]])).thenReturn(Future("1234"))
    when(receiptService.createReceipt("123-user", "1234")).thenReturn(Future(receipt))

    val content = "file content".getBytes
    val multipartForm =
      Multipart.FormData(Multipart.FormData.BodyPart.Strict(
        "random_field",
        HttpEntity(`application/octet-stream`, content),
        Map("filename" -> "receipt.png")))

    Post("/user/123-user/receipt", multipartForm) ~> routes ~> check {
      status shouldBe BadRequest
      contentType shouldBe `application/json`
      responseAs[ErrorResponse] shouldEqual ErrorResponse("Request is missing required form field 'receipt'")
    }
  }

  it should "add a file to existing receipt" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User("123-user", "name", "hash")))
    }
    val authenticationDirective: AuthenticationDirective[User] = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
    when(authenticator.bearerToken(true)).thenReturn(authenticationDirective)

    val receipt = ReceiptEntity(userId = "123-user")
    when(fileService.save(any[Source[ByteString, Any]])).thenReturn(Future("1234"))
    when(receiptService.createReceipt("123-user", "1234")).thenReturn(Future(receipt))

    val content = "file content".getBytes
    val multipartForm =
      Multipart.FormData(Multipart.FormData.BodyPart.Strict(
        "receipt",
        HttpEntity(`application/octet-stream`, content),
        Map("filename" -> "receipt.png")))

    Post("/user/123-user/receipt/1234/file", multipartForm) ~> routes ~> check {
      status shouldBe Created
      contentType shouldBe `application/json`
      responseAs[ReceiptEntity].id shouldBe receipt.id
    }
  }

  it should "authenticate a user" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User("id", "name", "hash")))
    }

    val authenticationDirective: AuthenticationDirective[User] = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    when(authenticator.basic(any[Option[FiniteDuration]])).thenReturn(authenticationDirective)

    val validCredentials = BasicHttpCredentials("John", "p4ssw0rd")
    Get("/token/create") ~> addCredentials(validCredentials) ~> routes ~> check {

      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[String] should include ("access_token")
    }
  }

  it should "not authenticate user" in {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(Left(HttpChallenge("MyAuth", "MyRealm")))
    }

    val authenticationDirective: AuthenticationDirective[User] = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    when(authenticator.basic(any[Option[FiniteDuration]])).thenReturn(authenticationDirective)

    val validCredentials = BasicHttpCredentials("John", "p4ssw0rd")
    Get("/token/create") ~> addCredentials(validCredentials) ~> routes ~> check {

      status shouldEqual Unauthorized
      contentType shouldBe `application/json`
      responseAs[ErrorResponse] shouldEqual ErrorResponse("The supplied authentication is invalid")
    }
  }

  it should "renew token" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User("id", "name", "hash")))
    }

    val authenticationDirective: AuthenticationDirective[User] = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    when(authenticator.bearerToken(true)).thenReturn(authenticationDirective)

    Get("/token/renew", OAuth2AccessTokenResponse("bearer", "token_str", 1000)) ~> routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[String] should include ("access_token")
    }
  }

  it should "not renew token" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(Left(HttpChallenge("MyAuth", "MyRealm")))
    }

    val authenticationDirective: AuthenticationDirective[User] = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    when(authenticator.bearerToken(true)).thenReturn(authenticationDirective)

    Get("/token/renew", OAuth2AccessTokenResponse("bearer", "token_str", 1000)) ~> routes ~> check {
      status shouldEqual Unauthorized
      contentType shouldBe `application/json`
      responseAs[ErrorResponse] shouldEqual ErrorResponse("The supplied authentication is invalid")
    }
  }

}