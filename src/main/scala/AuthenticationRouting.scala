import java.util.Date

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import de.choffmeister.auth.common.{OAuth2AccessTokenResponse, JsonWebToken}
import de.choffmeister.auth.akkahttp.Authenticator
import model.User
import spray.json.JsString

import scala.concurrent.duration._

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

class AuthenticationRouting(authenticator: Authenticator[User]) extends JsonProtocols {

  val bearerTokenSecret: Array[Byte] = "secret-no-one-knows".getBytes
  val bearerTokenLifetime: FiniteDuration = 60.minutes

  val routes = pathPrefix("token" / "create") { // curl -X GET -u "user2:pass2" http://localhost:9000/token/create
    get {
      // Here we can send valid username/password HTTP basic authentication
      // and get a JWT for it. If wrong credentials were given, then this
      // route is not completed before 1 second has passed. This makes timing
      // attacks harder, since an attacker cannot distinguish between wrong
      // username and existing username, but wrong password.
      authenticator.basic(Some(1000.millis))(user => completeWithToken(user, bearerTokenSecret, bearerTokenLifetime))
    }
  } ~
    path("token" / "renew") {
      get {
        // Here we can send an expired JWT via HTTP bearer authentication and
        // get a renewed JWT for it.
        authenticator.bearerToken(acceptExpired = true)(user => completeWithToken(user, bearerTokenSecret, bearerTokenLifetime))
      }
    }

  private def completeWithToken(user: User, secret: Array[Byte], lifetime: FiniteDuration): Route = {
    val now = System.currentTimeMillis / 1000L * 1000L

    val token = JsonWebToken(
      createdAt = new Date(now),
      expiresAt = new Date(now + lifetime.toMillis * 1000L),
      subject = user.id.toString,
      claims = Map("name" -> JsString(user.userName))
    )
    val tokenStr = JsonWebToken.write(token, secret)

    complete(OAuth2AccessTokenResponse("bearer", tokenStr, lifetime.toMillis))
  }
}
