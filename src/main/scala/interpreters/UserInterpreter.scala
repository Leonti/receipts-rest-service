package interpreters

import cats.~>
import model.User
import ops.UserOps._
import repository.UserRepository
import service.{GoogleOauthService, TokenType}

import scala.concurrent.Future

class UserInterpreter(userRepository: UserRepository, googleOauthService: GoogleOauthService) extends (UserOp ~> Future) {

  def apply[A](i: UserOp[A]): Future[A] = i match {
    case FindUserById(id: String) => userRepository.findUserById(id)
    case FindUserByUsername(username: String) => userRepository.findUserByUserName(username)
    case SaveUser(user: User) => userRepository.save(user)
    case GetValidatedGoogleTokenInfo(tokenValue: String, tokenType: TokenType) =>
      googleOauthService.fetchAndValidateTokenInfo(tokenValue, tokenType)
  }
}
