import model.{AccessToken, UserInfo}
import TestConfig._
import cats.effect.IO
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._
import org.http4s.circe.CirceEntityCodec._

import routing.OpenIdToken

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class UserTestUtils(httpClient: Client[IO]) {

  def createUser(): Future[(UserInfo, AccessToken)] = (for {
      accessToken  <- new Auth0Api(httpClient).createUserAndGetAccessToken()
      userInfo <- httpClient.expect[UserInfo](
        POST(
          Uri.unsafeFromString(s"$appHostPort/oauth/openid"),
          OpenIdToken(accessToken)
        )
      )
    } yield (userInfo, AccessToken(accessToken))).unsafeToFuture()

}
