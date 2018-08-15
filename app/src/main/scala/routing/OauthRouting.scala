package routing

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import model._
import service._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

case class OpenIdToken(token: String)

object OpenIdToken {
  implicit val openIdTokenDecoder: Decoder[OpenIdToken] = deriveDecoder
  implicit val openIdTokenEncoder: Encoder[OpenIdToken] = deriveEncoder
}

class OauthRouting(userPrograms: UserPrograms[Future])(implicit system: ActorSystem,
                                                       executor: ExecutionContextExecutor,
                                                       materializer: ActorMaterializer) {

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
