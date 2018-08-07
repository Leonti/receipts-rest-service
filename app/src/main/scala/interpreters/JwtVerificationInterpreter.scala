package interpreters

import algebras.JwtVerificationAlg
import cats.Id
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import model.SubClaim

import scala.util.Try

class JwtVerificationInterpreter(bearerTokenSecret: Array[Byte]) extends JwtVerificationAlg[Id] {
  override def verify(token: String): Either[String, SubClaim] = {
    val algorithmHS = Algorithm.HMAC256(bearerTokenSecret)
    val verifier = JWT.require(algorithmHS)
      .build()
    val jwtTry = Try(verifier.verify(token))

    jwtTry.toEither.left.map(_.getMessage).map(t => SubClaim(t.getClaim("sub").toString))
  }
}
