package service

import java.time.Instant

import authentication.OAuth2AccessTokenResponse
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import model.User

import scala.concurrent.duration._

object JwtTokenGenerator {
  private val bearerTokenLifetime: FiniteDuration     = 60.minutes
  private val bearerPathTokenLifetime: FiniteDuration = 5.minutes

  val generateToken: (User, Long, Array[Byte]) => OAuth2AccessTokenResponse = (user, currentTimeMillis, bearerTokenSecret) => {

    val algorithmHS = Algorithm.HMAC256(bearerTokenSecret)
    val token = JWT
      .create()
      .withIssuer("self")
      .withClaim("sub", s"${user.id}")
      .withClaim("name", user.userName)
      .withExpiresAt(java.util.Date.from(Instant.ofEpochSecond(currentTimeMillis / 1000L + bearerTokenLifetime.toSeconds)))
      .sign(algorithmHS)

    OAuth2AccessTokenResponse("bearer", token, bearerTokenLifetime.toMillis)
  }

  val generatePathToken: (String, Long, Array[Byte]) => OAuth2AccessTokenResponse = (path, currentTimeMillis, bearerTokenSecret) => {

    val algorithmHS = Algorithm.HMAC256(bearerTokenSecret)
    val token = JWT
      .create()
      .withIssuer("self")
      .withClaim("sub", path)
      .withExpiresAt(java.util.Date.from(Instant.ofEpochSecond(currentTimeMillis / 1000L + bearerPathTokenLifetime.toSeconds)))
      .sign(algorithmHS)

    OAuth2AccessTokenResponse("bearer", token, bearerPathTokenLifetime.toSeconds)
  }

}
