package service

import java.util.Date

import com.typesafe.config.ConfigFactory
import de.choffmeister.auth.common.{JsonWebToken, OAuth2AccessTokenResponse}
import model.User
import spray.json.JsString

import scala.concurrent.duration._

object JwtTokenGenerator {

  val config = ConfigFactory.load()
  val bearerTokenSecret: Array[Byte] = config.getString("tokenSecret").getBytes
  val bearerTokenLifetime: FiniteDuration = 60.minutes

  val generateToken: User => OAuth2AccessTokenResponse = (user) => {
    val now = System.currentTimeMillis / 1000L * 1000L

    val token = JsonWebToken(
      createdAt = new Date(now),
      expiresAt = new Date(now + bearerTokenLifetime.toMillis * 1000L),
      subject = user.id.toString,
      claims = Map("name" -> JsString(user.userName))
    )
    val tokenStr = JsonWebToken.write(token, bearerTokenSecret)

    OAuth2AccessTokenResponse("bearer", tokenStr, bearerTokenLifetime.toMillis)
  }

}
