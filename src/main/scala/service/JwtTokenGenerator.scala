package service

import java.time.Instant
import de.choffmeister.auth.common.{JsonWebToken, OAuth2AccessTokenResponse}
import model.User
import spray.json.JsString

import scala.concurrent.duration._

object JwtTokenGenerator {
  private val bearerTokenLifetime: FiniteDuration     = 60.minutes
  private val bearerPathTokenLifetime: FiniteDuration = 5.minutes

  val generateToken: (User, Long, Array[Byte]) => OAuth2AccessTokenResponse = (user, currentTimeMillis, bearerTokenSecret) => {

    val token = JsonWebToken(
      expiresAt = Instant.ofEpochSecond(currentTimeMillis / 1000L + bearerTokenLifetime.toSeconds),
      claims = Map("sub" -> JsString(user.id.toString()), "name" -> JsString(user.userName))
    )
    val tokenStr = JsonWebToken.write(token, bearerTokenSecret)

    OAuth2AccessTokenResponse("bearer", tokenStr, bearerTokenLifetime.toMillis)
  }

  val generatePathToken: (String, Long, Array[Byte]) => OAuth2AccessTokenResponse = (path, currentTimeMillis, bearerTokenSecret) => {
    val now = System.currentTimeMillis / 1000L * 1000L

    val token = JsonWebToken(
      expiresAt = Instant.ofEpochSecond(currentTimeMillis / 1000L + bearerPathTokenLifetime.toSeconds),
      claims = Map("sub" -> JsString(path))
    )
    val tokenStr = JsonWebToken.write(token, bearerTokenSecret)

    OAuth2AccessTokenResponse("bearer", tokenStr, bearerPathTokenLifetime.toSeconds)
  }

}
