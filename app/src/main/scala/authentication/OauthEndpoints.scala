package authentication

import cats.effect.Effect
import cats.implicits._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import model.AccessToken
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.io._
import user.{UserInfo, UserPrograms}

case class OpenIdToken(token: String)

object OpenIdToken {
  implicit val openIdTokenDecoder: Decoder[OpenIdToken] = deriveDecoder
  implicit val openIdTokenEncoder: Encoder[OpenIdToken] = deriveEncoder
}

class OauthEndpoints[F[_]: Effect](userPrograms: UserPrograms[F]) {

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "oauth" / "openid" =>
      for {
        openIdToken <- req.as[OpenIdToken]
        userInfo <- userPrograms
          .validateOpenIdUser(AccessToken(openIdToken.token))
          .map(user => UserInfo(id = user.id, userName = user.username))
      } yield Response(status = Status.Created).withEntity(userInfo): Response[F]
  }

}
