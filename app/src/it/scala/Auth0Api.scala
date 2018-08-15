import TestConfig._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity, StatusCode}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import io.circe.generic.auto._

import scala.concurrent.Future
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

object Auth0Api {
  implicit val system       = ActorSystem()
  implicit val materializer = ActorMaterializer()

  private def requestAuth0RequestToken(): Future[Auth0TokenResponse] = {
    val tokenRequest = Auth0TokenRequest(
      client_id = auth0ApiClientId,
      client_secret = auth0ApiClientSecret,
      audience = auth0ApiAudience
    )

    for {
      requestEntity <- Marshal(tokenRequest).to[RequestEntity]
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = "https://leonti.au.auth0.com/oauth/token",
          entity = requestEntity
        ))
      auth0Token <- Unmarshal(response.entity).to[Auth0TokenResponse]
    } yield auth0Token
  }

  private def requestUserToken(email: String, password: String): Future[Auth0TokenResponse] = {
    val passwordGrantRequest = PasswordGrantRequest(
      username = email,
      password = password,
      client_id = auth0ApiClientId,
      client_secret = auth0ApiClientSecret
    )

    for {
      requestEntity <- Marshal(passwordGrantRequest).to[RequestEntity]
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = "https://leonti.au.auth0.com/oauth/token",
          entity = requestEntity
        ))
      auth0Token <- Unmarshal(response.entity).to[Auth0TokenResponse]
    } yield auth0Token
  }

  def createUserAndGetAccessToken(): Future[String] = {
    val auth0CreateUserRequest = Auth0CreateUserRequest(
      email = s"ci_user_${java.util.UUID.randomUUID()}@mailinator.com"
    )

    for {
      auth0Token <- requestAuth0RequestToken()
      requestEntity <- Marshal(auth0CreateUserRequest).to[RequestEntity]
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"${auth0BaseUrl}users",
          entity = requestEntity,
          headers = List(Authorization(OAuth2BearerToken(auth0Token.access_token)))
        ))
      _ <- if (response.status == StatusCode.int2StatusCode(201)) Future.successful(()) else {
        println("Failed to create a user in auth0")
        Future.failed(new RuntimeException("Failed to create a user in auth0"))
      }
      userAccessToken <- requestUserToken(auth0CreateUserRequest.email, auth0CreateUserRequest.password)
    } yield userAccessToken.access_token
  }

}
