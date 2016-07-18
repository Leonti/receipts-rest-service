import akka.http.scaladsl.testkit.ScalatestRouteTest
import model.{CreateUserRequest, JsonProtocols, User, UserInfo}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.ContentTypes._
import routing.UserRouting
import service.UserService

import scala.concurrent.Future
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.headers.{HttpChallenge, HttpCredentials}
import akka.http.scaladsl.server.directives.{AuthenticationResult, SecurityDirectives}

class UserRoutingSpec extends FlatSpec with Matchers with ScalatestRouteTest with MockitoSugar with JsonProtocols {



  def createAuthentication(user: User) = {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(user))
    }
    SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
  }

  it should "create a user" in {

    val createUserRequest = CreateUserRequest(userName = "userName", password = "password")
    val user = User(userName = "userName", passwordHash = "hash")

    val userService = mock[UserService]
    when(userService.createUser(createUserRequest)).thenReturn(Future(Right(user)))

    val userRouting = new UserRouting(userService, createAuthentication(User(id = "123-user", userName = "name", passwordHash = "hash")))

    Post("/user/create", createUserRequest) ~> userRouting.routes ~> check {
      status shouldBe Created
      contentType shouldBe `application/json`
      responseAs[UserInfo] shouldBe UserInfo(user)
    }
  }

  it should "respond with appropriate error if user already exist" in {

    val createUserRequest = CreateUserRequest(userName = "userName", password = "password")

    val userService = mock[UserService]
    when(userService.createUser(CreateUserRequest(userName = "userName", password = "password")))
      .thenReturn(Future(Left("User already exist")))

    val userRouting = new UserRouting(userService, createAuthentication(User(id = "123-user", userName = "name", passwordHash = "hash")))

    Post("/user/create", createUserRequest) ~> userRouting.routes ~> check {
      status shouldBe Conflict
      contentType shouldBe `application/json`
      responseAs[String] should include ("error creating user")
    }
  }

  it should "respond with InternalServerError on failure" in {

    val userService = mock[UserService]
    val createUserRequest = CreateUserRequest(userName = "userName", password = "password")
    when(userService.createUser(createUserRequest)).thenReturn(Future.failed(new RuntimeException("test exception")))

    val userRouting = new UserRouting(userService, createAuthentication(User(id = "123-user", userName = "name", passwordHash = "hash")))
    Post("/user/create", createUserRequest) ~> userRouting.routes ~> check {
      status shouldBe InternalServerError
      contentType shouldBe `application/json`
      responseAs[String] should include ("server failure")
    }
  }

  it should "should show user info" in {

    val userService = mock[UserService]
    val user = User(id = "123-user", userName = "name", passwordHash = "hash")

    val userRouting = new UserRouting(userService, createAuthentication(user))
    Get("/user/info") ~> userRouting.routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[UserInfo] shouldBe UserInfo(user)
    }
  }

}
