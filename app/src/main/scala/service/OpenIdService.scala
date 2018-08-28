package service

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContextExecutor, Future}
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.ConfigFactory
import model.{AccessToken, ExternalUserInfo}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class OpenIdUserInfo(
    sub: String,
    email: String,
    email_verified: Boolean
)

object OpenIdUserInfo {
  implicit val openIdUserInfoDecoder: Decoder[OpenIdUserInfo] = deriveDecoder
  implicit val openIdUserInfoEncoder: Encoder[OpenIdUserInfo] = deriveEncoder
}

class OpenIdService()(implicit system: ActorSystem, executor: ExecutionContextExecutor, materializer: ActorMaterializer) {

  private val config = ConfigFactory.load()

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
