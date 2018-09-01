package service

import scala.concurrent.ExecutionContextExecutor
import cats.effect.IO
import model.{AccessToken, ExternalUserInfo}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.circe.CirceEntityCodec._

case class OpenIdUserInfo(
    sub: String,
    email: String,
    email_verified: Boolean
)

object OpenIdUserInfo {
  implicit val openIdUserInfoDecoder: Decoder[OpenIdUserInfo] = deriveDecoder
  implicit val openIdUserInfoEncoder: Encoder[OpenIdUserInfo] = deriveEncoder
}

class OpenIdService(httpClient: Client[IO])(implicit ec: ExecutionContextExecutor) {

  def fetchAndValidateTokenInfo(accessToken: AccessToken): IO[ExternalUserInfo] =
    httpClient
      .expect[OpenIdUserInfo](
        GET(
          Uri.uri("https://leonti.au.auth0.com/userinfo"),
          Authorization(Credentials.Token(AuthScheme.Bearer, accessToken.value))
        ))
      .map(userInfo => ExternalUserInfo(email = userInfo.email, sub = userInfo.sub))
}
