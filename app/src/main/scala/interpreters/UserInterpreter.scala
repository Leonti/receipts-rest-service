package interpreters

import model.User
import algebras.UserAlg
import repository.UserRepository
import service.{GoogleOauthService, GoogleTokenInfo, TokenType}

import scala.concurrent.Future

class UserInterpreterTagless(userRepository: UserRepository, googleOauthService: GoogleOauthService) extends UserAlg[Future] {
  override def findUserById(id: String): Future[Option[User]]             = userRepository.findUserById(id)
  override def findUserByUsername(username: String): Future[Option[User]] = userRepository.findUserByUserName(username)
  override def saveUser(user: User): Future[User]                         = userRepository.save(user)
  override def getValidatedGoogleTokenInfo(tokenValue: String, tokenType: TokenType): Future[GoogleTokenInfo] =
    googleOauthService.fetchAndValidateTokenInfo(tokenValue, tokenType)
}
