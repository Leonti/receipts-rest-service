package routing

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import authentication.OAuth2AccessTokenResponse
import model.{ErrorResponse, JsonProtocols}
import service._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

case class GoogleToken(token: String)

class OauthRouting(userPrograms: UserPrograms[Future])(implicit system: ActorSystem,
                                                       executor: ExecutionContextExecutor,
                                                       materializer: ActorMaterializer)
    extends JsonProtocols {

  private implicit val googleTokenFormat = jsonFormat1(GoogleToken)

  private val validateTokenWithUserCreation: (GoogleToken, TokenType) => Route = (googleToken, tokenType) => {
    val tokenFuture: Future[OAuth2AccessTokenResponse] = userPrograms.validateGoogleUser(googleToken, tokenType)

    onComplete(tokenFuture) { (tokenResult: Try[OAuth2AccessTokenResponse]) =>
      tokenResult match {
        case Success(token) => complete(Created -> token)
        case Failure(t: Throwable) => {
          println(s"Authentication exception $t")
          complete(BadRequest -> ErrorResponse("Failed to authenticate"))
        }
      }
    }
  }

  val routes: Route = {
    path("oauth" / "google-access-token") {
      post {
        entity(as[GoogleToken]) { googleToken =>
          validateTokenWithUserCreation(googleToken, AccessToken)
        }
      }
    } ~
    path("oauth" / "google-id-token") {
      post {
        entity(as[GoogleToken]) { googleToken =>
          validateTokenWithUserCreation(googleToken, IdToken)
        }
      }
    }
  }

}
