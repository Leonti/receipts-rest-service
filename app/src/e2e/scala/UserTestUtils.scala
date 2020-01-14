import model.AccessToken
import TestConfig._
import authentication.OpenIdToken
import cats.effect.IO
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._
import org.http4s.circe.CirceEntityCodec._
import user.UserInfo

class UserTestUtils(httpClient: Client[IO]) {

  def createUser: IO[(UserInfo, AccessToken)] =
    for {
      accessToken <- new Auth0Api(httpClient).createUserAndGetAccessToken()
      userInfo <- httpClient.expect[UserInfo](
        POST(
          OpenIdToken(accessToken),
          Uri.unsafeFromString(s"$appHostPort/oauth/openid")
        )
      )
    } yield (userInfo, AccessToken(accessToken))

}
