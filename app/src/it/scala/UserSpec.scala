import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.Unmarshal
import authentication.OAuth2AccessTokenResponse
import model.{CreateUserRequest, JsonProtocols, UserInfo}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import UserTestUtils._
import TestConfig._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.scalatest.time.{Millis, Seconds, Span}

class UserSpec extends FlatSpec with Matchers with ScalaFutures with JsonProtocols {

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(15, Seconds), interval = Span(500, Millis))

  it should "create a user" in {

    val username          = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    whenReady(createUser(createUserRequest)) { userInfo =>
      userInfo.userName shouldBe username
    }
  }

  it should "authenticate a user" in {
    val username          = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    whenReady(createUser(createUserRequest).flatMap(authenticateUser(_))) { accessToken =>
      accessToken.expiresIn shouldBe 3600000
    }
  }

  it should "renew token" in {
    val username          = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val renewedTokenFuture = for {
      userInfo    <- createUser(createUserRequest)
      accessToken <- authenticateUser(userInfo)
      response <- Http().singleRequest(
        HttpRequest(uri = s"$appHostPort/token/renew", headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))))
      renewedToken <- Unmarshal(response.entity).to[OAuth2AccessTokenResponse]
    } yield renewedToken

    whenReady(renewedTokenFuture) { renewedToken =>
      renewedToken.expiresIn shouldBe 3600000
    }
  }

  it should "display user info" in {
    val username          = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val userInfoFuture = for {
      userInfo    <- createUser(createUserRequest)
      accessToken <- authenticateUser(userInfo)
      response <- Http().singleRequest(
        HttpRequest(uri = s"$appHostPort/user/info", headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))))
      userInfo <- Unmarshal(response.entity).to[UserInfo]
    } yield userInfo

    whenReady(userInfoFuture) { userInfo =>
      userInfo.userName shouldBe username
    }
  }

}
