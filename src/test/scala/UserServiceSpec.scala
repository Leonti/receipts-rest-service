import model.{CreateUserRequest, User}
import org.scalatest._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar

import org.mockito.Matchers.any
import repository.UserRepository
import service.UserService
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Right

import org.scalatest.time.{Millis, Seconds, Span}


class UserServiceSpec extends FlatSpec with Matchers with MockitoSugar with ScalaFutures {

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  it should "return existing user" in {

    val repository = mock[UserRepository]
    val user = Future(Some(User(
      id = "id",
      userName = "name",
      passwordHash = "pbkdf2:hmac-sha1:10000:128:Z0+02KhCGr4xkEzZIVKfu9qfoYR+ZgrNUDF/C3JJTwk=:W7LYY7TanDr++ha3507kCg==")))

    when(repository.findUserById("id")).thenReturn(user)
    when(repository.findUserByUserName("userName")).thenReturn(user)

    val userService = new UserService(repository)

    userService.findById("id") shouldBe user
    userService.findByUserName("name")
  }

  it should "return error if user already exist" in {

    val repository = mock[UserRepository]

    when(repository.findUserByUserName("userName"))
      .thenReturn(Future(Some(User(id = "id", userName = "name", passwordHash = "hash"))))

    val userService = new UserService(repository)

    whenReady(userService.createUser(CreateUserRequest("userName", "password"))) { result =>
      result shouldBe Left("User already exists")
    }

  }

  it should "create user if doesn't exist yet" in {
    val repository = mock[UserRepository]

    val user = User(userName = "userName", passwordHash = "passwordHash")
    when(repository.findUserByUserName("userName")).thenReturn(Future(None))
    when(repository.save(any[User])).thenReturn(Future(user))

    val userService = new UserService(repository)

    whenReady(userService.createUser(CreateUserRequest("userName", "password"))) { result =>
      result shouldBe Right(user)
    }
  }

}
