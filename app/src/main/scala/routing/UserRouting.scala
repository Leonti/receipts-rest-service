package routing

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import model._
import service.UserPrograms

import scala.concurrent.{ExecutionContextExecutor, Future}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.directives.AuthenticationDirective

class UserRouting(userPrograms: UserPrograms[Future], authenticaton: AuthenticationDirective[User])(
    implicit executor: ExecutionContextExecutor)
    extends JsonProtocols {

  val routes = path("user" / "info") {
    get {
      authenticaton { (user: User) =>
        complete(OK -> UserInfo(id = user.id, userName = user.userName))
      }
    }
  }
}
