import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.{OAuth2BearerToken, BasicHttpCredentials, Authorization}
import de.choffmeister.auth.common.OAuth2AccessTokenResponse
import model.{CreateUserRequest, UserInfo, JsonProtocols}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}

import akka.http.scaladsl.unmarshalling.Unmarshal

import akka.http.scaladsl.marshalling.Marshal

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer

import scala.concurrent.Future

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import scala.concurrent.ExecutionContext.Implicits.global

class E2eSpec extends FlatSpec with Matchers with ScalaFutures  with JsonProtocols {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(15, Seconds), interval = Span(500, Millis))

  def createUser(createUserRequest: CreateUserRequest): Future[UserInfo] = {
    for {
      request <- Marshal(createUserRequest).to[RequestEntity]
      response <- Http().singleRequest(HttpRequest(method = HttpMethods.POST, uri = s"http://localhost:9000/user/create", entity = request))
      userInfo <- Unmarshal(response.entity).to[UserInfo]
    } yield userInfo
  }

  def authenticateUser(userInfo: UserInfo): Future[OAuth2AccessTokenResponse] = {
    for {
      response <- Http().singleRequest(HttpRequest(uri = s"http://localhost:9000/token/create",
        headers = List(Authorization(BasicHttpCredentials(userInfo.userName, "password")))))
      accessToken <- Unmarshal(response.entity).to[OAuth2AccessTokenResponse]
    } yield accessToken
  }

  it should "create a user" in {

    val username = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    whenReady(createUser(createUserRequest)) { userInfo =>

      userInfo.userName shouldBe username
    }
  }

  it should "authenticate a user" in {
    val username = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    whenReady(createUser(createUserRequest).flatMap(authenticateUser(_))) { accessToken =>
      println(accessToken)
      accessToken.expiresIn shouldBe 3600000
    }
  }

  it should "renew token" in {
    val username = "ci_user_" + java.util.UUID.randomUUID()
    val createUserRequest = CreateUserRequest(username, "password")

    val renewedTokenFuture = for {
      userInfo <- createUser(createUserRequest)
      accessToken <- authenticateUser(userInfo)
      response <- Http().singleRequest(HttpRequest(uri = s"http://localhost:9000/token/renew",
        headers = List(Authorization(OAuth2BearerToken(accessToken.accessToken)))))
      renewedToken <- Unmarshal(response.entity).to[OAuth2AccessTokenResponse]
    } yield renewedToken

    whenReady(renewedTokenFuture) { renewedToken =>
      println(renewedToken)
      renewedToken.expiresIn shouldBe 3600000
    }
  }
}
