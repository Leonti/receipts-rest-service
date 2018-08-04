package service

import algebras._
import cats.Monad
import cats.implicits._
import cats.free.Free
import de.choffmeister.auth.common.{OAuth2AccessTokenResponse, PBKDF2, PasswordHasher, Plain}
import model.{CreateUserRequest, User}
import ops.{RandomOps, TokenOps, UserOps}
import ops.TokenOps.TokenOp
import ops.UserOps.UserOp
import routing.GoogleToken

import scala.util.Right
import freek._
import ops.RandomOps.RandomOp

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

object UserService {

  private val hasher = new PasswordHasher("pbkdf2", "hmac-sha1" :: "10000" :: "128" :: Nil, List(PBKDF2, Plain))

  type PRG = UserOp :|: RandomOp :|: NilDSL
  val PRG = DSL.Make[PRG]

  def createUser(createUserRequest: CreateUserRequest): Free[PRG.Cop, Either[String, User]] =
    for {
      existingUser <- UserOps.FindUserByUsername(createUserRequest.userName).freek[PRG]: Free[PRG.Cop, Option[User]]
      guid         <- RandomOps.GenerateGuid().freek[PRG]
      result <- if (existingUser.isDefined) {
        Free.pure[PRG.Cop, Either[String, User]](Left("User already exists"))
      } else {
        UserOps
          .SaveUser(
            User(
              id = guid,
              userName = createUserRequest.userName,
              passwordHash = hasher.hash(createUserRequest.password)
            ))
          .freek[PRG]
          .map(user => Right(user))
      }
    } yield result

  def findByUserNameWithPassword(userName: String, password: String): Free[PRG.Cop, Option[User]] =
    for {
      user <- UserOps.FindUserByUsername(userName).freek[PRG]: Free[PRG.Cop, Option[User]]
    } yield user.filter(u => hasher.validate(u.passwordHash, password))

  def findById(id: String): Free[PRG.Cop, Option[User]] =
    UserOps.FindUserById(id).freek[PRG]

  type UserAndTokenPGR = UserOp :|: TokenOp :|: NilDSL
  val UserAndTokenPGR = DSL.Make[UserAndTokenPGR]

  def validateGoogleUser(googleToken: GoogleToken, tokenType: TokenType): Free[UserAndTokenPGR.Cop, OAuth2AccessTokenResponse] =
    for {
      tokenInfo    <- UserOps.GetValidatedGoogleTokenInfo(googleToken.token, tokenType).freek[UserAndTokenPGR]
      existingUser <- UserOps.FindUserByUsername(tokenInfo.email).freek[UserAndTokenPGR]: Free[UserAndTokenPGR.Cop, Option[User]]
      user <- if (existingUser.isDefined) {
        Free.pure[UserAndTokenPGR.Cop, User](existingUser.get)
      } else {
        UserOps.SaveUser(User(userName = tokenInfo.email)).freek[UserAndTokenPGR]
      }
      token <- TokenOps.GenerateUserToken(user).freek[UserAndTokenPGR]
    } yield token
}
