package routing

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import model._
import service._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import cats.Monad
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

case class OpenIdTokenOld(token: String)

object OpenIdTokenOld {
  implicit val openIdTokenDecoderOld: Decoder[OpenIdToken] = deriveDecoder
  implicit val openIdTokenEncoderOld: Encoder[OpenIdToken] = deriveEncoder
}

class OauthRouting[F[_]: ToScalaFuture: Monad](userPrograms: UserPrograms[F])(implicit system: ActorSystem,
                                                       executor: ExecutionContextExecutor,
                                                       materializer: ActorMaterializer, tsf: ToScalaFuture[F]) {

  private val validateTokenWithUserCreation: OpenIdToken => Route = token => {
    val userFuture: Future[User] = tsf(userPrograms.validateOpenIdUser(AccessToken(token.token)))

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
