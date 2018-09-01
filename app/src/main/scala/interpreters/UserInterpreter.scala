package interpreters

import model.{AccessToken, ExternalUserInfo, User}
import algebras.UserAlg
import cats.effect.IO
import repository.UserRepository
import service.OpenIdService

class UserInterpreter(userRepository: UserRepository, openIdService: OpenIdService) extends UserAlg[IO] {
  override def findUserByExternalId(id: String): IO[Option[User]]     = IO.fromFuture(IO(userRepository.findUserByExternalId(id)))
  override def findUserByUsername(username: String): IO[Option[User]] = IO.fromFuture(IO(userRepository.findUserByUserName(username)))
  override def saveUser(user: User): IO[User]                         = IO.fromFuture(IO(userRepository.save(user)))
  override def getExternalUserInfoFromAccessToken(accessToken: AccessToken): IO[ExternalUserInfo] = openIdService.fetchAndValidateTokenInfo(accessToken)
}
