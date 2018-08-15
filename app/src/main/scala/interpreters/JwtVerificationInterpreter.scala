package interpreters

import algebras.JwtVerificationAlg
import cats.Id
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import model.SubClaim
import com.auth0.jwk.{UrlJwkProvider, GuavaCachedJwkProvider}
import com.auth0.jwt.interfaces.RSAKeyProvider
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

import scala.util.Try

class JwtVerificationInterpreter(bearerTokenSecret: Array[Byte]) extends JwtVerificationAlg[Id] {

  private val httpProvider = new UrlJwkProvider("https://leonti.au.auth0.com/")
  private val jwkProvider = new GuavaCachedJwkProvider(httpProvider)

  val keyProvider: RSAKeyProvider = new RSAKeyProvider() {
    override def getPublicKeyById(kid: String): RSAPublicKey =  {
      val publicKey = jwkProvider.get(kid).getPublicKey
      publicKey.asInstanceOf[RSAPublicKey]
    }
    override def getPrivateKey: RSAPrivateKey = null
    override def getPrivateKeyId: String = ""
  }

  override def verify(token: String): Either[String, SubClaim] = {
    val algorithm = Algorithm.RSA256(keyProvider)
    val verifier = JWT
      .require(algorithm)
      .withAudience("receipts-backend")
      .build()
    val jwtTry = Try(verifier.verify(token))

    jwtTry.toEither.left.map(_.getMessage).map(t => SubClaim(t.getClaim("sub").asString()))
  }
}
