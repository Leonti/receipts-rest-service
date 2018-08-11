package interpreters

import model.{AccessToken, Email, User}
import algebras.UserAlg
import repository.UserRepository
import service.Auth0Service

import scala.concurrent.Future

class UserInterpreter(userRepository: UserRepository, auth0Service: Auth0Service) extends UserAlg[Future] {
  override def findUserById(id: String): Future[Option[User]]                   = userRepository.findUserById(id)
  override def findUserByUsername(username: String): Future[Option[User]]       = userRepository.findUserByUserName(username)
  override def saveUser(user: User): Future[User]                               = userRepository.save(user)
  override def getEmailFromAccessToken(accessToken: AccessToken): Future[Email] = auth0Service.fetchAndValidateTokenInfo(accessToken)
}
