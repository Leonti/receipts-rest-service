package interpreters

import java.time.Instant

import algebras.TokenAlg
import authentication.OAuth2AccessTokenResponse
import cats.Monad
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import model.SubClaim

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.util.Try

class TokenInterpreter[F[_]: Monad](bearerTokenSecret: Array[Byte]) extends TokenAlg[F] {
  private val bearerPathTokenLifetime: FiniteDuration = 5.minutes

  override def generatePathToken(path: String): F[OAuth2AccessTokenResponse] =
    Monad[F].pure {
      val algorithmHS = Algorithm.HMAC256(bearerTokenSecret)
      val token = JWT
        .create()
        .withIssuer("self")
        .withClaim("sub", path)
        .withExpiresAt(java.util.Date.from(Instant.ofEpochSecond(System.currentTimeMillis() / 1000L + bearerPathTokenLifetime.toSeconds)))
        .sign(algorithmHS)

      OAuth2AccessTokenResponse("bearer", token, bearerPathTokenLifetime.toSeconds)
    }

  override def verifyPathToken(token: String): F[Either[String, SubClaim]] = Monad[F].pure {
    val algorithmHS = Algorithm.HMAC256(bearerTokenSecret)
    val verifier = JWT
      .require(algorithmHS)
      .build() // FIXME - add time verification
    val jwtTry = Try(verifier.verify(token))

    jwtTry.toEither.left.map(_.getMessage).map(t => SubClaim(t.getClaim("sub").asString()))
  }
}
