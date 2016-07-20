package routing

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import de.choffmeister.auth.common.OAuth2AccessTokenResponse
import model.{ErrorResponse, JsonProtocols, User}
import service._
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import spray.json._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

case class GoogleToken(token: String)

class OauthRouting(userService: UserService, googleOauthService: GoogleOauthService)
                  (implicit system: ActorSystem, executor: ExecutionContextExecutor, materializer: ActorMaterializer) extends JsonProtocols {

  implicit val googleTokenFormat = jsonFormat1(GoogleToken)

  private val validateTokenWithUserCreation: (GoogleToken, TokenType) => Route = (googleToken, tokenType) => {
    val tokenFuture: Future[OAuth2AccessTokenResponse] = for {
      tokenInfo <- googleOauthService.fetchAndValidateTokenInfo(googleToken.token, tokenType)
      optionUser <- userService.findByUserName(tokenInfo.email)
      user: User <- optionUser match {
        case Some(user) => Future.successful(user)
        case None => userService.createGoogleUser(tokenInfo.email)
      }
      token <- Future.successful(JwtTokenGenerator.generateToken(user))
    } yield token

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

  val routes = {
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
