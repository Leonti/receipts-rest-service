package authentication

import java.time.Instant

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.Try

class PathToken(bearerTokenSecret: Array[Byte]) {
  private val bearerPathTokenLifetime: FiniteDuration = 5.minutes

  def generatePathToken(path: String): OAuth2AccessTokenResponse = {
    val algorithmHS = Algorithm.HMAC256(bearerTokenSecret)
    val token = JWT
      .create()
      .withIssuer("self")
      .withClaim("sub", path)
      .withExpiresAt(java.util.Date.from(Instant.ofEpochSecond(System.currentTimeMillis() / 1000L + bearerPathTokenLifetime.toSeconds)))
      .sign(algorithmHS)

    OAuth2AccessTokenResponse("bearer", token, bearerPathTokenLifetime.toSeconds)
  }

  def verifyPathToken(token: String): Either[String, SubClaim] = {
    val algorithmHS = Algorithm.HMAC256(bearerTokenSecret)
    val verifier = JWT
      .require(algorithmHS)
      .build() // FIXME - add time verification
    val jwtTry = Try(verifier.verify(token))

    jwtTry.toEither.left.map(_.getMessage).map(t => SubClaim(t.getClaim("sub").asString()))
  }
}
