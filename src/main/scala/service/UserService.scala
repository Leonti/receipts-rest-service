package service

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

object UserService {

  private val hasher = new PasswordHasher("pbkdf2", "hmac-sha1" :: "10000" :: "128" :: Nil, List(PBKDF2, Plain))

  type PRG = UserOp :|: RandomOp :|: NilDSL
  val PRG = DSL.Make[PRG]

  def createUser(createUserRequest: CreateUserRequest): Free[PRG.Cop, Either[String, User]] = for {
    existingUser <- UserOps.FindUserByUsername(createUserRequest.userName).freek[PRG]: Free[PRG.Cop, Option[User]]
    guid <- RandomOps.GenerateGuid().freek[PRG]
    result <- if (existingUser.isDefined) {
      Free.pure[PRG.Cop, Either[String, User]](Left("User already exists"))
    } else {
      UserOps.SaveUser(User(
        id = guid,
        userName = createUserRequest.userName,
        passwordHash = hasher.hash(createUserRequest.password)
      )).freek[PRG].map(user => Right(user))
    }
  } yield result

  def findByUserNameWithPassword(userName: String, password: String): Free[PRG.Cop, Option[User]] = for {
    user <- UserOps.FindUserByUsername(userName).freek[PRG]: Free[PRG.Cop, Option[User]]
  } yield user.filter(u => hasher.validate(u.passwordHash, password))

  def findById(id: String): Free[PRG.Cop, Option[User]] =
    UserOps.FindUserById(id).freek[PRG]

  type UserAndTokenPGR = UserOp :|: TokenOp :|: NilDSL
  val UserAndTokenPGR = DSL.Make[UserAndTokenPGR]

  def validateGoogleUser(googleToken: GoogleToken, tokenType: TokenType): Free[UserAndTokenPGR.Cop, OAuth2AccessTokenResponse] =
    for {
      tokenInfo <- UserOps.GetValidatedGoogleTokenInfo(googleToken.token, tokenType).freek[UserAndTokenPGR]
      existingUser <- UserOps.FindUserByUsername(tokenInfo.email).freek[UserAndTokenPGR]: Free[UserAndTokenPGR.Cop, Option[User]]
      user <- if (existingUser.isDefined) {
        Free.pure[UserAndTokenPGR.Cop, User](existingUser.get)
      } else {
        UserOps.SaveUser(User(userName = tokenInfo.email)).freek[UserAndTokenPGR]
      }
      token <- TokenOps.GenerateUserToken(user).freek[UserAndTokenPGR]
    } yield token
}
