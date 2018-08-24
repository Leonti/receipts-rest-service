package interpreters

import com.typesafe.config.ConfigFactory
import algebras.TokenAlg
import authentication.OAuth2AccessTokenResponse
import cats.effect.IO
import service.JwtTokenGenerator

class TokenInterpreter extends TokenAlg[IO] {

  private val config                         = ConfigFactory.load()
  private val bearerTokenSecret: Array[Byte] = config.getString("tokenSecret").getBytes
  override def generatePathToken(path: String): IO[OAuth2AccessTokenResponse] =
    IO(JwtTokenGenerator.generatePathToken(path, System.currentTimeMillis, bearerTokenSecret))
}
