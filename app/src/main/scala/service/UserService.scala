package service

import algebras._
import cats.Monad
import cats.implicits._
import authentication.OAuth2AccessTokenResponse
import de.choffmeister.auth.common.{PBKDF2, PasswordHasher, Plain}
import model.{CreateUserRequest, User}
import routing.GoogleToken
import scala.util.Right

import scala.language.higherKinds

class UserPrograms[F[_]: Monad](userAlg: UserAlg[F], randomAlg: RandomAlg[F], tokenAlg: TokenAlg[F]) {
  import userAlg._, randomAlg._, tokenAlg._

  private val hasher = new PasswordHasher("pbkdf2", "hmac-sha1" :: "10000" :: "128" :: Nil, List(PBKDF2, Plain))

  def createUser(createUserRequest: CreateUserRequest): F[Either[String, User]] =
    for {
      existingUser <- findUserByUsername(createUserRequest.userName)
      guid         <- generateGuid()
      result <- if (existingUser.isDefined) {
        Monad[F].pure(Left("User already exists"))
      } else {
        saveUser(
          User(
            id = guid,
            userName = createUserRequest.userName,
            passwordHash = hasher.hash(createUserRequest.password)
          ))
          .map(user => Right(user))
      }
    } yield result

  def findByUserNameWithPassword(userName: String, password: String): F[Option[User]] =
    for {
      user <- findUserByUsername(userName)
    } yield user.filter(u => hasher.validate(u.passwordHash, password))

  def findById(id: String): F[Option[User]] = findUserById(id)

  def validateGoogleUser(googleToken: GoogleToken, tokenType: TokenType): F[OAuth2AccessTokenResponse] =
    for {
      tokenInfo    <- getValidatedGoogleTokenInfo(googleToken.token, tokenType)
      existingUser <- findUserByUsername(tokenInfo.email)
      user <- if (existingUser.isDefined) {
        Monad[F].pure(existingUser.get)
      } else {
        saveUser(User(userName = tokenInfo.email))
      }
      token <- generateUserToken(user)
    } yield token
}
