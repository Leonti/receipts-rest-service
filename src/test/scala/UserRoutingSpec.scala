import akka.http.scaladsl.testkit.ScalatestRouteTest
import model.{UserInfo, User, CreateUserRequest}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, FlatSpec}

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.ContentTypes._

import scala.concurrent.Future
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

class UserRoutingSpec extends FlatSpec with Matchers with ScalatestRouteTest with MockitoSugar with ScalaFutures with JsonProtocols {

  val userService = mock[UserService]

  val userRouting = new UserRouting(userService)

  it should "create a user" in {

    val createUserRequest = CreateUserRequest(userName = "userName", password = "password")
    val user = User(userName = "userName", passwordHash = "hash")
    when(userService.createUser(createUserRequest)).thenReturn(Future(Right(user)))

    Post("/user/create", createUserRequest) ~> userRouting.routes ~> check {
      status shouldBe Created
      contentType shouldBe `application/json`
      responseAs[UserInfo] shouldBe UserInfo(user)
    }
  }

  it should "respond with appropriate error if user already exist" in {

    val createUserRequest = CreateUserRequest(userName = "userName", password = "password")
    when(userService.createUser(CreateUserRequest(userName = "userName", password = "password")))
      .thenReturn(Future(Left("User already exist")))

    Post("/user/create", createUserRequest) ~> userRouting.routes ~> check {
      status shouldBe Conflict
      contentType shouldBe `application/json`
      responseAs[String] should include ("error creating user")
    }
  }

  it should "respond with InternalServerError on failure" in {

    val createUserRequest = CreateUserRequest(userName = "userName", password = "password")
    when(userService.createUser(createUserRequest)).thenReturn(Future.failed(new RuntimeException("test exception")))

    Post("/user/create", createUserRequest) ~> userRouting.routes ~> check {
      status shouldBe InternalServerError
      contentType shouldBe `application/json`
      responseAs[String] should include ("server failure")
    }
  }

}
