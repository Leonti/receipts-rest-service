package service

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContextExecutor, Future}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.ConfigFactory
import model.{AccessToken, ExternalUserInfo}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

case class OpenIdUserInfo(
    sub: String,
    email: String,
    email_verified: Boolean
)

class OpenIdService()(implicit system: ActorSystem, executor: ExecutionContextExecutor, materializer: ActorMaterializer)
    extends DefaultJsonProtocol {

  implicit val openIdTokenInfoFormat: RootJsonFormat[OpenIdUserInfo] = jsonFormat3(OpenIdUserInfo)
  private val config                                               = ConfigFactory.load()

  val fetchAndValidateTokenInfo: AccessToken => Future[ExternalUserInfo] = accessToken => {

    for {
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"https://leonti.au.auth0.com/userinfo",
          headers = List(Authorization(OAuth2BearerToken(accessToken.value)))
        ))
      userInfo <- Unmarshal(response.entity).to[OpenIdUserInfo]
    } yield ExternalUserInfo(email = userInfo.email, sub = userInfo.sub)
  }
}
