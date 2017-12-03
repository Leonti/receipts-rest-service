package routing

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import model.{ErrorResponse, JsonProtocols, User}

import scala.concurrent.duration._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import authentication.JwtAuthenticator
import com.typesafe.config.ConfigFactory
import service.JwtTokenGenerator

class AuthenticationRouting(authenticator: JwtAuthenticator[User]) extends JsonProtocols {

  private val config                         = ConfigFactory.load()
  private val bearerTokenSecret: Array[Byte] = config.getString("tokenSecret").getBytes

  def myRejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case AuthenticationFailedRejection(cause, challenge) =>
          complete((Unauthorized -> ErrorResponse(s"The supplied authentication is invalid ${cause.toString}")))
      }
      .result()

  val routes =
    handleRejections(myRejectionHandler) {
      pathPrefix("token" / "create") { // curl -X GET -u "user2:pass2" http://localhost:9000/token/create
        get {
          // Here we can send valid username/password HTTP basic authentication
          // and get a JWT for it. If wrong credentials were given, then this
          // route is not completed before 1 second has passed. This makes timing
          // attacks harder, since an attacker cannot distinguish between wrong
          // username and existing username, but wrong password.
          authenticator.basic(Some(1000.millis))(user =>
            complete(JwtTokenGenerator.generateToken(user, System.currentTimeMillis(), bearerTokenSecret)))
        }
      } ~
      path("token" / "renew") {
        get {
          // Here we can send an expired JWT via HTTP bearer authentication and
          // get a renewed JWT for it.
          authenticator.bearerTokenOrCookie(acceptExpired = true)(user =>
            complete(JwtTokenGenerator.generateToken(user, System.currentTimeMillis(), bearerTokenSecret)))
        }
      }
    }
}
