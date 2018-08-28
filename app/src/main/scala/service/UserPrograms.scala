package service

import algebras._
import cats.Monad
import cats.implicits._
import model.{AccessToken, User}

import scala.language.higherKinds

class UserPrograms[F[_]: Monad](userAlg: UserAlg[F], randomAlg: RandomAlg[F]) {
  import userAlg._, randomAlg._

  def findUserByExternalId(id: String): F[Option[User]] = userAlg.findUserByExternalId(id)

  def validateOpenIdUser(accessToken: AccessToken): F[User] =
    for {
      externalUserInfo <- getExternalUserInfoFromAccessToken(accessToken)
      existingUser     <- findUserByUsername(externalUserInfo.email)
      user <- if (existingUser.isDefined) {
        saveUser(
          existingUser.get.copy(
            externalIds = externalUserInfo.sub +: existingUser.get.externalIds.filterNot(id => id == externalUserInfo.sub)
          ))
      } else {
        generateGuid().flatMap(userId =>
          saveUser(User(id = userId, userName = externalUserInfo.email, externalIds = List(externalUserInfo.sub))))

      }
    } yield user
}
