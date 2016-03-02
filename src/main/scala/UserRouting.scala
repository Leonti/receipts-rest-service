import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import model.{CreateUserRequest, ErrorResponse, User, UserInfo}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

class UserRouting(userService: UserService) extends JsonProtocols {

  val routes = pathPrefix("user" / "create") {
    // curl -H "Content-Type: application/json" -i -X POST -d '{"userName": "leonti3", "password": "pass1"}' http://localhost:9000/user/create
    (post & entity(as[CreateUserRequest])) { createUserRequest =>

      val userFuture: Future[Either[String, User]] = userService.createUser(createUserRequest)
      onComplete(userFuture) { (result: Try[Either[String, User]]) =>
        result match {
          case Success(user: Either[String, User]) => user match {
            case Right(user) => complete(Created -> UserInfo(user))
            case Left(error) => complete(Conflict -> ErrorResponse(s"error creating user: ${error}"))
          }
          case Failure(t: Throwable) => complete(InternalServerError -> ErrorResponse(s"server failure: ${t}"))
        }
      }
    }
  }
}
