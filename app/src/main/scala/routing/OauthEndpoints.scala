package routing
import cats.Monad
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.finch._
import io.finch.syntax._
import io.finch.circe._
import cats.implicits._
import model.{AccessToken, UserInfo}
import service.UserPrograms

case class OpenIdToken(token: String)

object OpenIdToken {
  implicit val openIdTokenDecoder: Decoder[OpenIdToken] = deriveDecoder
  implicit val openIdTokenEncoder: Encoder[OpenIdToken] = deriveEncoder
}

class OauthEndpoints[F[_]: ToTwitterFuture: Monad](userPrograms: UserPrograms[F]) {

  val validateWithUserCreation: Endpoint[UserInfo] = post("oauth" :: "openid" :: jsonBody[OpenIdToken]) { openIdToken: OpenIdToken =>
    userPrograms
      .validateOpenIdUser(AccessToken(openIdToken.token))
      .map(user => UserInfo(id = user.id, userName = user.userName))
      .map(Created)
  }

}
