/*
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenge, HttpCredentials}
import akka.http.scaladsl.server.directives.{SecurityDirectives, AuthenticationDirective, AuthenticationResult}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import authentication.JwtAuthenticator
import model.{JsonProtocols, ErrorResponse, User}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, FlatSpec}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes._
import routing.AuthenticationRouting

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class AuthenticationRoutingSpec extends FlatSpec with Matchers with ScalatestRouteTest with MockitoSugar with JsonProtocols {

  // TODO revisit when switched to Auth0 or Cognito



  it should "authenticate a user" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "id", userName = "name", passwordHash = "hash")))
    }

    val authenticationDirective: AuthenticationDirective[User] =
      SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    when(authenticator.basic(any[Option[FiniteDuration]])).thenReturn(authenticationDirective)

    val authenticationRouting = new AuthenticationRouting(authenticator)

    val validCredentials = BasicHttpCredentials("John", "p4ssw0rd")
    Get("/token/create") ~> addCredentials(validCredentials) ~> authenticationRouting.routes ~> check {

      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[String] should include("access_token")
    }
  }

  it should "not authenticate user" in {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(Left(HttpChallenge("MyAuth", "MyRealm")))
    }

    val authenticationDirective: AuthenticationDirective[User] =
      SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    when(authenticator.basic(any[Option[FiniteDuration]])).thenReturn(authenticationDirective)

    val authenticationRouting = new AuthenticationRouting(authenticator)

    val validCredentials = BasicHttpCredentials("John", "p4ssw0rd")
    Get("/token/create") ~> addCredentials(validCredentials) ~> authenticationRouting.routes ~> check {

      status shouldEqual Unauthorized
      contentType shouldBe `application/json`
      responseAs[ErrorResponse] shouldEqual ErrorResponse("The supplied authentication is invalid CredentialsRejected")
    }
  }

  it should "renew token" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User(id = "id", userName = "name", passwordHash = "hash")))
    }

    val authenticationDirective: AuthenticationDirective[User] =
      SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    when(authenticator.bearerTokenOrCookie(true)).thenReturn(authenticationDirective)

    val authenticationRouting = new AuthenticationRouting(authenticator)

    // OAuth2AccessTokenResponse("bearer", "token_str", 1000)
    Get("/token/renew") ~> authenticationRouting.routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[String] should include("access_token")
    }
  }

  it should "not renew token" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(Left(HttpChallenge("MyAuth", "MyRealm")))
    }

    val authenticationDirective: AuthenticationDirective[User] =
      SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    when(authenticator.bearerTokenOrCookie(true)).thenReturn(authenticationDirective)

    val authenticationRouting = new AuthenticationRouting(authenticator)

    // OAuth2AccessTokenResponse("bearer", "token_str", 1000)
    Get("/token/renew") ~> authenticationRouting.routes ~> check {
      status shouldEqual Unauthorized
      contentType shouldBe `application/json`
      responseAs[ErrorResponse] shouldEqual ErrorResponse("The supplied authentication is invalid CredentialsMissing")
    }
  }


}
*/