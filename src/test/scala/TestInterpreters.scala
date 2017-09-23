import java.io.{ByteArrayInputStream, File}

import akka.stream.IOResult
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import cats.~>
import interpreters.Interpreters
import model._
import ocr.model.OcrTextAnnotation
import ops.FileOps._
import ops.OcrOps.{OcrImage, OcrOp, SaveOcrResult}
import ops.PendingFileOps._
import ops.RandomOps.{GenerateGuid, GetTime, RandomOp, TmpFile}
import ops.ReceiptOps._
import ops.TokenOps.{GeneratePathToken, GenerateUserToken, TokenOp}
import ops.UserOps._
import ops.EnvOps._
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

  class RandomInterpreter(id: String, time: Long = 0, file: File = new File("")) extends (RandomOp ~> Future) {

    def apply[A](i: RandomOp[A]): Future[A] = i match {
      case GenerateGuid() => Future.successful(id)
      case GetTime()      => Future.successful(time)
      case TmpFile()      => Future.successful(file)
    }

  }

  class FileInterpreter() extends (FileOp ~> Future) {

    def apply[A](i: FileOp[A]): Future[A] = i match {
      case MoveFile(src: File, dst: File) => Future.successful((): Unit)
      case SubmitPendingFile(pendingFile: PendingFile) =>
        Future.successful(pendingFile)
      case SubmitToFileQueue(userId: String, receiptId: String, file: File, fileExt: String, pendingFileId: String) =>
        Future.successful("")
      case SaveFile(userId: String, file: File, ext: String) => Future.successful(List())
      case FetchFile(userId: String, fileId: String) =>
        Future.successful(StreamConverters.fromInputStream(() => new ByteArrayInputStream("some text".getBytes)))
      case DeleteFile(userId: String, fileId: String)                             => Future.successful(())
      case RemoveFile(_)                                                          => Future.successful(())
      case SourceToFile(source: Source[ByteString, Future[IOResult]], file: File) => Future.successful(file)
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

  class OcrInterpreter() extends (OcrOp ~> Future) {

    val testAnnotation = OcrTextAnnotation(text = "Parsed ocr text", pages = List())

    def apply[A](i: OcrOp[A]): Future[A] = i match {
      case OcrImage(file: File) => Future.successful(testAnnotation)
      case SaveOcrResult(userId: String, receiptId: String, ocrResult: OcrTextAnnotation) =>
        Future.successful(OcrEntity(userId = userId, id = receiptId, result = testAnnotation))
    }

  }

  class PendingFileInterpreter() extends (PendingFileOp ~> Future) {

    def apply[A](i: PendingFileOp[A]): Future[A] = i match {
      case SavePendingFile(pendingFile: PendingFile) => Future.successful(pendingFile)
      case FindPendingFileForUserId(userId: String)  => Future.successful(List())
      case DeletePendingFileById(id: String)         => Future.successful(())
      case DeleteAllPendingFiles()                   => Future.successful(())
    }

  }

  class EnvInterpreter() extends (EnvOp ~> Future) {

    def apply[A](i: EnvOp[A]): Future[A] = i match {
      case GetEnv(key: String) => Future.successful("mock_value")
    }
  }

  val testInterpreters = Interpreters(
    userInterpreter = new UserInterpreter(List(), ""),
    tokenInterpreter = new TokenInterpreter(System.currentTimeMillis(), "secret"),
    randomInterpreter = new RandomInterpreter("", 0),
    fileInterpreter = new FileInterpreter(),
    receiptInterpreter = new ReceiptInterpreter(List(), List()),
    ocrInterpreter = new OcrInterpreter(),
    pendingFileInterpreter = new PendingFileInterpreter(),
    envInterpreter = new EnvInterpreter()
  )

}
