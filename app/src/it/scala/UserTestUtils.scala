import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import model.{AccessToken, UserInfo}
import TestConfig._
import routing.OpenIdToken

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

package object UserTestUtils extends JsonProtocols {

  implicit val system       = ActorSystem()
  implicit val materializer = ActorMaterializer()

  def createUser(): Future[(UserInfo, AccessToken)] = {
    for {
      accessToken  <- Auth0Api.createUserAndGetAccessToken()
      request <- Marshal(OpenIdToken(accessToken)).to[RequestEntity]
      response <- Http().singleRequest(HttpRequest(method = HttpMethods.POST, uri = s"$appHostPort/oauth/openid", entity = request))
      userInfo <- Unmarshal(response.entity).to[UserInfo]
    } yield (userInfo, AccessToken(accessToken))
  }

}
