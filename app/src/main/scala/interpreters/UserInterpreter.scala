package interpreters

import cats.~>
import model.User
import ops.UserOps._
import algebras.UserAlg
import repository.UserRepository
import service.{GoogleOauthService, GoogleTokenInfo, TokenType}

import scala.concurrent.Future

class UserInterpreter(userRepository: UserRepository, googleOauthService: GoogleOauthService) extends (UserOp ~> Future) {

  def apply[A](i: UserOp[A]): Future[A] = i match {
    case FindUserById(id: String)             => userRepository.findUserById(id)
    case FindUserByUsername(username: String) => userRepository.findUserByUserName(username)
    case SaveUser(user: User)                 => userRepository.save(user)
    case GetValidatedGoogleTokenInfo(tokenValue: String, tokenType: TokenType) =>
      googleOauthService.fetchAndValidateTokenInfo(tokenValue, tokenType)
  }
}

class UserInterpreterTagless(userRepository: UserRepository, googleOauthService: GoogleOauthService) extends UserAlg[Future] {
  override def findUserById(id: String): Future[Option[User]] = userRepository.findUserById(id)
  override def findUserByUsername(
      username: String): Future[Option[User]] = userRepository.findUserByUserName(username)
  override def saveUser(user: User): Future[User] = userRepository.save(user)
  override def getValidatedGoogleTokenInfo(
    tokenValue: String,
    tokenType: TokenType): Future[GoogleTokenInfo] = googleOauthService.fetchAndValidateTokenInfo(tokenValue, tokenType)
}
