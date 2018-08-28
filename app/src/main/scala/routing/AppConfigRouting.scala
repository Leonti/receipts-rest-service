package routing

import com.typesafe.config.ConfigFactory
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class AppConfig(googleClientId: String)

object AppConfig {
  implicit val appConfigDecoder: Decoder[AppConfig] = deriveDecoder
  implicit val appConfigEncoder: Encoder[AppConfig] = deriveEncoder
}

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

class AppConfigRouting {
  val config = ConfigFactory.load()

  val routes = path("config") {
    get {
      complete(OK -> AppConfig(googleClientId = config.getString("googleClientId")))
    }
  }
}
