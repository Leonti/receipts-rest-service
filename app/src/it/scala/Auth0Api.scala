import TestConfig._
import cats.effect.IO
import io.circe.generic.auto._
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.circe.CirceEntityCodec._

import scala.concurrent.ExecutionContext.Implicits.global

case class Auth0TokenRequest(
    client_id: String,
    client_secret: String,
    audience: String,
    grant_type: String = "client_credentials"
                             )

case class PasswordGrantRequest(
    grant_type: String = "password",
    username: String,
    password: String,
    audience: String = "receipts-backend",
    scope: String = "openid email",
    client_id: String,
    client_secret: String
                               )

case class Auth0TokenResponse(
    access_token: String,
    token_type: String
                             )

case class Auth0CreateUserRequest(
    connection: String = auth0ConnectionName,
    email: String,
    password: String = "password",
    email_verified: Boolean = true
                                 )

class Auth0Api(httpClient: Client[IO]) {

  private def requestAuth0AccessToken(): IO[Auth0TokenResponse] = httpClient.expect[Auth0TokenResponse](
    POST(
     Uri.uri("https://leonti.au.auth0.com/oauth/token"),
      Auth0TokenRequest(
        client_id = auth0ApiClientId,
        client_secret = auth0ApiClientSecret,
        audience = auth0ApiAudience
      )
    )
  )

  private def requestUserToken(email: String, password: String): IO[Auth0TokenResponse] = httpClient.expect[Auth0TokenResponse](
    POST(
      Uri.uri("https://leonti.au.auth0.com/oauth/token"),
      PasswordGrantRequest(
        username = email,
        password = password,
        client_id = auth0ApiClientId,
        client_secret = auth0ApiClientSecret
      )
    )
  )

  private def createAuth0User(auth0CreateUserRequest: Auth0CreateUserRequest, accessToken: String): IO[Unit] = httpClient.fetch(
    POST(
      Uri.unsafeFromString(s"${auth0BaseUrl}users"),
      auth0CreateUserRequest,
      Authorization(Credentials.Token(AuthScheme.Bearer, accessToken))
    )
  ) {
    case Status.Created(_) => IO.pure(())
    case _ => IO.raiseError(new RuntimeException("Failed to create a user in auth0"))
  }

  def createUserAndGetAccessToken(): IO[String] = {
    val auth0CreateUserRequest = Auth0CreateUserRequest(
      email = s"ci_user_${java.util.UUID.randomUUID()}@mailinator.com"
    )

    for {
      auth0Token <- requestAuth0AccessToken()
      _ <- createAuth0User(auth0CreateUserRequest, auth0Token.access_token)
      userAccessToken <- requestUserToken(auth0CreateUserRequest.email, auth0CreateUserRequest.password)
    } yield userAccessToken.access_token
  }

}
