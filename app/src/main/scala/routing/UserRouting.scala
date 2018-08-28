package routing

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import model._

import scala.concurrent.ExecutionContextExecutor
import akka.http.scaladsl.server.directives.AuthenticationDirective
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

class UserRouting(authenticaton: AuthenticationDirective[User])(
    implicit executor: ExecutionContextExecutor) {

  val routes = path("user" / "info") {
    get {
      authenticaton { user: User =>
        complete(OK -> UserInfo(id = user.id, userName = user.userName))
      }
    }
  }
}
