package routing

import com.typesafe.config.ConfigFactory
import spray.json.DefaultJsonProtocol

case class AppConfig(googleClientId: String)

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

class AppConfigRouting extends DefaultJsonProtocol {
  implicit val appConfigFormat = jsonFormat1(AppConfig)
  val config                   = ConfigFactory.load()

  val routes = path("config") {
    get {
      complete(OK -> AppConfig(googleClientId = config.getString("googleClientId")))
    }
  }
}
