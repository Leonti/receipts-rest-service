package interpreters

import model.{AccessToken, ExternalUserInfo, User}
import algebras.UserAlg
import repository.UserRepository
import service.OpenIdService

import scala.concurrent.Future

class UserInterpreter(userRepository: UserRepository, openIdService: OpenIdService) extends UserAlg[Future] {
  override def findUserByExternalId(id: String): Future[Option[User]]     = userRepository.findUserByExternalId(id)
  override def findUserByUsername(username: String): Future[Option[User]] = userRepository.findUserByUserName(username)
  override def saveUser(user: User): Future[User]                         = userRepository.save(user)
  override def getExternalUserInfoFromAccessToken(accessToken: AccessToken): Future[ExternalUserInfo] =
    openIdService.fetchAndValidateTokenInfo(accessToken)
}
