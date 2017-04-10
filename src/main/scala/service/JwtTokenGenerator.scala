package service

import java.time.Instant
import java.util.Date

import com.typesafe.config.ConfigFactory
import de.choffmeister.auth.common.{JsonWebToken, OAuth2AccessTokenResponse}
import model.User
import spray.json.JsString

import scala.concurrent.duration._

object JwtTokenGenerator {

  private val config                                  = ConfigFactory.load()
  private val bearerTokenSecret: Array[Byte]          = config.getString("tokenSecret").getBytes
  private val bearerTokenLifetime: FiniteDuration     = 60.minutes
  private val bearerPathTokenLifetime: FiniteDuration = 5.minutes

  val generateToken: User => OAuth2AccessTokenResponse = (user) => {
    val now = System.currentTimeMillis / 1000L * 1000L

    val token = JsonWebToken(
      expiresAt = Instant.ofEpochSecond(System.currentTimeMillis / 1000L + bearerTokenLifetime.toSeconds),
      claims = Map("sub" -> JsString(user.id.toString()), "name" -> JsString(user.userName))
    )
    val tokenStr = JsonWebToken.write(token, bearerTokenSecret)

    OAuth2AccessTokenResponse("bearer", tokenStr, bearerTokenLifetime.toMillis)
  }

  val generatePathToken: String => OAuth2AccessTokenResponse = (path) => {
    val now = System.currentTimeMillis / 1000L * 1000L

    val token = JsonWebToken(
      expiresAt = Instant.ofEpochSecond(System.currentTimeMillis / 1000L + bearerPathTokenLifetime.toSeconds),
      claims = Map("sub" -> JsString(path))
    )
    val tokenStr = JsonWebToken.write(token, bearerTokenSecret)

    OAuth2AccessTokenResponse("bearer", tokenStr, bearerPathTokenLifetime.toSeconds)
  }

}
