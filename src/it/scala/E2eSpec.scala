import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, Authorization}
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

case class AccessToken(token_type: String, access_token: String, expires_in: Long)

class E2eSpec extends FlatSpec with Matchers with ScalaFutures  with JsonProtocols {

  implicit val accessTokenFormat = jsonFormat3(AccessToken)
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

    val accessTokenFuture = for {
      userInfo <- createUser(createUserRequest)
      response <- Http().singleRequest(HttpRequest(uri = s"http://localhost:9000/token/create",
        headers = List(Authorization(BasicHttpCredentials(username, "password")))))
      accessToken <- Unmarshal(response.entity).to[AccessToken]
    } yield accessToken

    whenReady(accessTokenFuture) { accessToken =>
      println(accessToken)
      accessToken.expires_in shouldBe 3600000
    }

  }
}
