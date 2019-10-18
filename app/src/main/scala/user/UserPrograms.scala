package user

import algebras._
import cats.Monad
import cats.implicits._
import model.AccessToken

class UserPrograms[F[_]: Monad](userAlg: UserAlg[F], randomAlg: RandomAlg[F]) {
  import randomAlg._

  def findUserByExternalId(id: String): F[Option[UserIds]] = userAlg.findByExternalId(id)

  def validateOpenIdUser(accessToken: AccessToken): F[UserIds] =
    for {
      externalUserInfo <- userAlg.getExternalUserInfoFromAccessToken(accessToken)
      existingUserIds  <- userAlg.findByUsername(externalUserInfo.email)
      userIdsToSave <- existingUserIds match {
        case userIds :: _ => Monad[F].pure(UserIds(id = userIds.id, username = userIds.username, externalId = externalUserInfo.sub))
        case Nil =>
          generateGuid().map(userId => UserIds(id = userId, username = externalUserInfo.email, externalId = externalUserInfo.sub))
      }
      _ <- userAlg.saveUserIds(userIdsToSave)
    } yield userIdsToSave
}
