package interpreters

import model.{AccessToken, Email, User}
import algebras.UserAlg
import repository.UserRepository
import service.OpenIdService

import scala.concurrent.Future

class UserInterpreter(userRepository: UserRepository, openIdService: OpenIdService) extends UserAlg[Future] {
  override def findUserById(id: String): Future[Option[User]]                   = userRepository.findUserById(id)
  override def findUserByUsername(username: String): Future[Option[User]]       = userRepository.findUserByUserName(username)
  override def saveUser(user: User): Future[User]                               = userRepository.save(user)
  override def getEmailFromAccessToken(accessToken: AccessToken): Future[Email] = openIdService.fetchAndValidateTokenInfo(accessToken)
}
