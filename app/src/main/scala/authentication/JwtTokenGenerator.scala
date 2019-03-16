package authentication
import java.time.Instant

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import user.UserIds
import scala.concurrent.duration._

import scala.concurrent.duration.FiniteDuration

object JwtTokenGenerator {
  private val bearerTokenLifetime: FiniteDuration     = 60.minutes
  private val bearerPathTokenLifetime: FiniteDuration = 5.minutes

  val generateToken: (UserIds, Long, Array[Byte]) => OAuth2AccessTokenResponse = (user, currentTimeMillis, bearerTokenSecret) => {

    val algorithmHS = Algorithm.HMAC256(bearerTokenSecret)
    val token = JWT
      .create()
      .withIssuer("self")
      .withClaim("sub", s"${user.id}")
      .withClaim("name", user.username)
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
