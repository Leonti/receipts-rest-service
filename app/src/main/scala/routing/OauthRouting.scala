package routing

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import model._
import service._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import spray.json.RootJsonFormat

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

case class OpenIdToken(token: String)

class OauthRouting(userPrograms: UserPrograms[Future])(implicit system: ActorSystem,
                                                       executor: ExecutionContextExecutor,
                                                       materializer: ActorMaterializer)
    extends JsonProtocols {

  private implicit val openIdTokenFormat: RootJsonFormat[OpenIdToken] = jsonFormat1(OpenIdToken)

  private val validateTokenWithUserCreation: OpenIdToken => Route = token => {
    val userFuture: Future[User] = userPrograms.validateOpenIdUser(AccessToken(token.token))

    onComplete(userFuture) { userTry: Try[User] =>
      userTry match {
        case Success(user) => complete(Created -> UserInfo(user))
        case Failure(t: Throwable) => {
          println(s"Authentication exception $t")
          complete(BadRequest -> ErrorResponse("Failed to authenticate"))
        }
      }
    }
  }

  val routes: Route = {
    path("oauth" / "openid") {
      post {
        entity(as[OpenIdToken]) { openIdToken =>
          validateTokenWithUserCreation(openIdToken)
        }
      }
    }
  }

}
