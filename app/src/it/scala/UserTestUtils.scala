import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import de.choffmeister.auth.common.OAuth2AccessTokenResponse
import model.{CreateUserRequest, JsonProtocols, UserInfo}
import TestConfig._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

package object UserTestUtils extends JsonProtocols {

  implicit val system       = ActorSystem()
  implicit val materializer = ActorMaterializer()

  def createUser(createUserRequest: CreateUserRequest): Future[UserInfo] = {
    for {
      request  <- Marshal(createUserRequest).to[RequestEntity]
      response <- Http().singleRequest(HttpRequest(method = HttpMethods.POST, uri = s"$appHostPort/user/create", entity = request))
      userInfo <- Unmarshal(response.entity).to[UserInfo]
    } yield userInfo
  }

  def authenticateUser(userInfo: UserInfo): Future[OAuth2AccessTokenResponse] = {
    for {
      response <- Http().singleRequest(
        HttpRequest(uri = s"$appHostPort/token/create",
                    headers = List(Authorization(BasicHttpCredentials(userInfo.userName, "password")))))
      accessToken <- Unmarshal(response.entity).to[OAuth2AccessTokenResponse]
    } yield accessToken
  }

}
