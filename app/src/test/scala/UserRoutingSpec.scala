import TestInterpreters._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import model.{CreateUserRequest, JsonProtocols, User, UserInfo}
import org.scalatest.{FlatSpec, Matchers}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.ContentTypes._
import routing.UserRouting

import scala.concurrent.Future
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.headers.{HttpChallenge, HttpCredentials}
import akka.http.scaladsl.server.directives.{AuthenticationResult, SecurityDirectives}
import service.UserPrograms
import cats.implicits._

class UserRoutingSpec extends FlatSpec with Matchers with ScalatestRouteTest with JsonProtocols {

  val userInterpreter = new UserInterpreterTagless(List(), "")
  val randomInterpreter = new RandomInterpreterTagless("", 0)
  val tokenInterpreter = new TokenInterpreterTagless(System.currentTimeMillis(), "secret")

  def createAuthentication(user: User) = {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(user))
    }
    SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)
  }

  it should "create a user" in {

    val createUserRequest = CreateUserRequest(userName = "userName", password = "password")
    val user              = User(id = "123-user-id", userName = "userName", passwordHash = "hash")

    val userRouting = new UserRouting(new UserPrograms(userInterpreter, new RandomInterpreterTagless(user.id), tokenInterpreter), createAuthentication(user))

    Post("/user/create", createUserRequest) ~> userRouting.routes ~> check {
      status shouldBe Created
      contentType shouldBe `application/json`
      responseAs[UserInfo] shouldBe UserInfo(user)
    }
  }

  it should "respond with appropriate error if user already exist" in {

    val createUserRequest = CreateUserRequest(userName = "userName", password = "password")
    val interpreters = TestInterpreters.testInterpreters.copy(
      userInterpreter = new UserInterpreter(List(User(userName = "userName", passwordHash = "hash")), ""))

    val userRouting = new UserRouting(new UserPrograms(
      new UserInterpreterTagless(List(User(userName = "userName", passwordHash = "hash")), ""),
      randomInterpreter,
      tokenInterpreter), createAuthentication(User(id = "123-user", userName = "name", passwordHash = "hash")))

    Post("/user/create", createUserRequest) ~> userRouting.routes ~> check {
      status shouldBe Conflict
      contentType shouldBe `application/json`
      responseAs[String] should include("error creating user")
    }
  }

  it should "should show user info" in {
    val user = User(id = "123-user", userName = "name", passwordHash = "hash")

    val userRouting = new UserRouting(new UserPrograms(userInterpreter, randomInterpreter, tokenInterpreter), createAuthentication(user))
    Get("/user/info") ~> userRouting.routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[UserInfo] shouldBe UserInfo(user)
    }
  }

}
