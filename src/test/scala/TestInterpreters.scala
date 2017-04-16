import java.io.File

import cats.~>
import interpreters.Interpreters
import model.{PendingFile, User}
import ops.FileOps.{FileOp, SubmitPendingFile, SubmitToFileQueue}
import ops.RandomOps.{GenerateGuid, RandomOp}
import ops.TokenOps.{GeneratePathToken, GenerateUserToken, TokenOp}
import ops.UserOps._
import service.{GoogleTokenInfo, JwtTokenGenerator, TokenType}

import scala.concurrent.Future

object TestInterpreters {

  class UserInterpreter(users: Seq[User], googleTokenEmail: String) extends (UserOp ~> Future) {

    def apply[A](i: UserOp[A]): Future[A] = i match {
      case FindUserById(id: String)             => Future.successful(users.find(_.id == id))
      case FindUserByUsername(username: String) => Future.successful(users.find(_.userName == username))
      case SaveUser(user: User)                 => Future.successful(user)
      case GetValidatedGoogleTokenInfo(tokenValue: String, tokenType: TokenType) =>
        Future.successful(GoogleTokenInfo(aud = "", sub = "", email = googleTokenEmail))
    }
  }

  class TokenInterpreter(currentTimeMillis: Long, bearerTokenSecret: String) extends (TokenOp ~> Future) {

    def apply[A](i: TokenOp[A]): Future[A] = i match {
      case GenerateUserToken(user: User) =>
        Future.successful(JwtTokenGenerator.generateToken(user, currentTimeMillis, bearerTokenSecret.getBytes))
      case GeneratePathToken(path: String) =>
        Future.successful(JwtTokenGenerator.generatePathToken(path, currentTimeMillis, bearerTokenSecret.getBytes))
    }

  }

  class RandomInterpreter(id: String) extends (RandomOp ~> Future) {

    def apply[A](i: RandomOp[A]): Future[A] = i match {
      case GenerateGuid() => Future.successful(id)
    }

  }

  class FileInterpreter() extends (FileOp ~> Future) {

    def apply[A](i: FileOp[A]): Future[A] = i match {
      case SubmitPendingFile(pendingFile: PendingFile) =>
        Future.successful(pendingFile)
      case SubmitToFileQueue(userId: String, receiptId: String, file: File, fileExt: String, pendingFileId: String) =>
        Future.successful("")
      //  case SaveFile(userId: String, file: File, ext: String) =>
      //  case FetchFile(userId: String, fileId: String) =>
      //  case DeleteFile(userId: String, fileId: String) =>
    }
  }

  val testInterpreters = Interpreters(
    userInterpreter = new UserInterpreter(List(), ""),
    tokenInterpreter = new TokenInterpreter(System.currentTimeMillis(), "secret"),
    randomInterpreter = new RandomInterpreter(""),
    fileInterpreter = new FileInterpreter()
  )

}
