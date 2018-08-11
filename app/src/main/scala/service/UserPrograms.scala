package service

import algebras._
import cats.Monad
import cats.implicits._
import model.{AccessToken, User}

import scala.language.higherKinds

class UserPrograms[F[_]: Monad](userAlg: UserAlg[F]) {
  import userAlg._

  def findById(id: String): F[Option[User]] = findUserById(id)

  def validateAuth0User(accessToken: AccessToken): F[User] =
    for {
      email        <- getEmailFromAccessToken(accessToken)
      existingUser <- findUserByUsername(email.value)
      user <- if (existingUser.isDefined) {
        Monad[F].pure(existingUser.get)
      } else {
        saveUser(User(userName = email.value))
      }
    } yield user
}
