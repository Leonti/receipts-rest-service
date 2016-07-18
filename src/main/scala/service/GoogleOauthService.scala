package service

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContextExecutor, Future}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.ConfigFactory
import spray.json.DefaultJsonProtocol


case class GoogleTokenInfo(aud: String, sub: String, email: String)

class GoogleOauthService()(implicit system: ActorSystem, executor: ExecutionContextExecutor, materializer: ActorMaterializer)
  extends DefaultJsonProtocol {

  implicit val googleTokenInfoFormat = jsonFormat3(GoogleTokenInfo)
  val config = ConfigFactory.load()

  val fetchTokenInfoWithAccessToken: (String) => Future[GoogleTokenInfo] = (accessToken) => {

    for {
      response <- Http().singleRequest(HttpRequest(
        method = HttpMethods.GET,
        uri = s"https://www.googleapis.com/oauth2/v3/tokeninfo?access_token=$accessToken"
      ))
      tokenInfo <- Unmarshal(response.entity).to[GoogleTokenInfo]
      validatedTokenInfo <- if (tokenInfo.aud == config.getString("googleClientId")) Future.successful(tokenInfo)
      else Future.failed(new RuntimeException("Invalid sub"))
    } yield validatedTokenInfo
  }


}
