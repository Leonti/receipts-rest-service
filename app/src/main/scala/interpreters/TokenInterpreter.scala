package interpreters

import com.typesafe.config.ConfigFactory
import model.User
import algebras.TokenAlg
import authentication.OAuth2AccessTokenResponse
import service.JwtTokenGenerator

import scala.concurrent.{ExecutionContext, Future}

// TODO do not use Future here
class TokenInterpreterTagless(implicit executor: ExecutionContext) extends TokenAlg[Future] {

  private val config                         = ConfigFactory.load()
  private val bearerTokenSecret: Array[Byte] = config.getString("tokenSecret").getBytes
  override def generateUserToken(user: User): Future[OAuth2AccessTokenResponse] =
    Future.successful(JwtTokenGenerator.generateToken(user, System.currentTimeMillis, bearerTokenSecret))
  override def generatePathToken(path: String): Future[OAuth2AccessTokenResponse] =
    Future.successful(JwtTokenGenerator.generatePathToken(path, System.currentTimeMillis, bearerTokenSecret))
}
