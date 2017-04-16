package interpreters

import cats.~>
import com.typesafe.config.ConfigFactory
import model.User
import ops.TokenOps.{GeneratePathToken, GenerateUserToken, TokenOp}
import service.JwtTokenGenerator

import scala.concurrent.{ExecutionContext, Future}

class TokenInterpreter(implicit executor: ExecutionContext) extends (TokenOp ~> Future) {

  private val config                                  = ConfigFactory.load()
  private val bearerTokenSecret: Array[Byte]          = config.getString("tokenSecret").getBytes

  def apply[A](i: TokenOp[A]): Future[A] = i match {
    case GenerateUserToken(user: User) => Future.successful(JwtTokenGenerator.generateToken(user, System.currentTimeMillis, bearerTokenSecret))
    case GeneratePathToken(path: String) => Future.successful(JwtTokenGenerator.generatePathToken(path, System.currentTimeMillis, bearerTokenSecret))
  }

}
