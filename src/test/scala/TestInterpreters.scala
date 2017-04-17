import java.io.File

import cats.~>
import interpreters.Interpreters
import model._
import ops.FileOps.{FileOp, SubmitPendingFile, SubmitToFileQueue}
import ops.RandomOps.{GenerateGuid, GetTime, RandomOp}
import ops.ReceiptOps._
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

  class RandomInterpreter(id: String, time: Long = 0) extends (RandomOp ~> Future) {

    def apply[A](i: RandomOp[A]): Future[A] = i match {
      case GenerateGuid() => Future.successful(id)
      case GetTime()      => Future.successful(time)
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

  class ReceiptInterpreter(receipts: Seq[ReceiptEntity], ocrs: Seq[OcrTextOnly]) extends (ReceiptOp ~> Future) {

    def apply[A](i: ReceiptOp[A]): Future[A] = i match {
      case GetReceipt(id: String)                                => Future.successful(receipts.find(_.id == id))
      case DeleteReceipt(id: String)                             => Future.successful(())
      case SaveReceipt(id: String, receipt: ReceiptEntity)       => Future.successful(receipt)
      case GetReceipts(ids: Seq[String])                         => Future.successful(receipts)
      case UserReceipts(userId: String)                          => Future.successful(receipts)
      case FindOcrByText(userId: String, query: String)          => Future.successful(ocrs)
      case AddFileToReceipt(receiptId: String, file: FileEntity) => Future.successful(())
    }

  }

  val testInterpreters = Interpreters(
    userInterpreter = new UserInterpreter(List(), ""),
    tokenInterpreter = new TokenInterpreter(System.currentTimeMillis(), "secret"),
    randomInterpreter = new RandomInterpreter("", 0),
    fileInterpreter = new FileInterpreter(),
    receiptInterpreter = new ReceiptInterpreter(List(), List())
  )

}
