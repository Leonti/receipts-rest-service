import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenge, HttpCredentials}
import akka.http.scaladsl.server.directives.{SecurityDirectives, AuthenticationDirective, AuthenticationResult}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.choffmeister.auth.akkahttp.Authenticator
import de.choffmeister.auth.common.OAuth2AccessTokenResponse
import model.{JsonProtocols, ErrorResponse, User}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, FlatSpec}

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes._
import routing.AuthenticationRouting

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class AuthenticationRoutingSpec extends FlatSpec with Matchers with ScalatestRouteTest with MockitoSugar with ScalaFutures with JsonProtocols {

  val authenticator = mock[Authenticator[User]]

  it should "authenticate a user" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User("id", "name", "hash")))
    }

    val authenticationDirective: AuthenticationDirective[User] = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    when(authenticator.basic(any[Option[FiniteDuration]])).thenReturn(authenticationDirective)

    val authenticationRouting = new AuthenticationRouting(authenticator)

    val validCredentials = BasicHttpCredentials("John", "p4ssw0rd")
    Get("/token/create") ~> addCredentials(validCredentials) ~> authenticationRouting.routes ~> check {

      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[String] should include ("access_token")
    }
  }

  it should "not authenticate user" in {
    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(Left(HttpChallenge("MyAuth", "MyRealm")))
    }

    val authenticationDirective: AuthenticationDirective[User] = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    when(authenticator.basic(any[Option[FiniteDuration]])).thenReturn(authenticationDirective)

    val authenticationRouting = new AuthenticationRouting(authenticator)

    val validCredentials = BasicHttpCredentials("John", "p4ssw0rd")
    Get("/token/create") ~> addCredentials(validCredentials) ~> authenticationRouting.routes ~> check {

      status shouldEqual Unauthorized
      contentType shouldBe `application/json`
      responseAs[ErrorResponse] shouldEqual ErrorResponse("The supplied authentication is invalid")
    }
  }

  it should "renew token" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(AuthenticationResult.success(User("id", "name", "hash")))
    }

    val authenticationDirective: AuthenticationDirective[User] = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    when(authenticator.bearerToken(true)).thenReturn(authenticationDirective)

    val authenticationRouting = new AuthenticationRouting(authenticator)

    Get("/token/renew", OAuth2AccessTokenResponse("bearer", "token_str", 1000)) ~> authenticationRouting.routes ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[String] should include ("access_token")
    }
  }

  it should "not renew token" in {

    def myUserPassAuthenticator(credentials: Option[HttpCredentials]): Future[Either[HttpChallenge, User]] = {
      Future(Left(HttpChallenge("MyAuth", "MyRealm")))
    }

    val authenticationDirective: AuthenticationDirective[User] = SecurityDirectives.authenticateOrRejectWithChallenge[User](myUserPassAuthenticator)

    when(authenticator.bearerToken(true)).thenReturn(authenticationDirective)

    val authenticationRouting = new AuthenticationRouting(authenticator)

    Get("/token/renew", OAuth2AccessTokenResponse("bearer", "token_str", 1000)) ~> authenticationRouting.routes ~> check {
      status shouldEqual Unauthorized
      contentType shouldBe `application/json`
      responseAs[ErrorResponse] shouldEqual ErrorResponse("The supplied authentication is invalid")
    }
  }
}
