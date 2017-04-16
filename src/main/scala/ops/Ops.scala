package ops

import java.io.File

import akka.stream.IOResult
import akka.stream.scaladsl.Source
import akka.util.ByteString
import de.choffmeister.auth.common.OAuth2AccessTokenResponse
import model.{FileEntity, PendingFile, ReceiptEntity, User}
import queue.Models.JobId
import service.{GoogleTokenInfo, TokenType}

import scala.concurrent.Future

object ReceiptOps {
  sealed trait ReceiptOp[A]

  case class GetReceipt(id: String)                                extends ReceiptOp[Option[ReceiptEntity]]
  case class DeleteReceipt(id: String)                             extends ReceiptOp[Unit]
  case class SaveReceipt(id: String, receipt: ReceiptEntity)       extends ReceiptOp[Unit]
  case class FindReceipts(userId: String, query: String)           extends ReceiptOp[List[ReceiptEntity]]
  case class UserReceipts(userId: String)                          extends ReceiptOp[List[ReceiptEntity]]
  case class AddFileToReceipt(receiptId: String, file: FileEntity) extends ReceiptOp[Unit]
}

object FileOps {
  sealed trait FileOp[A]

  case class SubmitPendingFile(id: String, userId: String, receiptId: String, file: File, fileExt: String) extends FileOp[PendingFile]
  case class SubmitToFileQueue(userId: String,
                               receiptId: String,
                               file: File,
                               fileExt: String,
                               pendingFileId: String) extends FileOp[JobId]
  case class SaveFile(userId: String, file: File, ext: String) extends FileOp[Seq[FileEntity]]
  case class FetchFile(userId: String, fileId: String)         extends FileOp[Source[ByteString, Future[IOResult]]]
  case class DeleteFile(userId: String, fileId: String)        extends FileOp[Unit]
}

object UserOps {
  sealed trait UserOp[A]

  case class FindUserById(id: String)                                              extends UserOp[Option[User]]
  case class FindUserByUsername(username: String)                                  extends UserOp[Option[User]]
  case class SaveUser(user: User)                                                  extends UserOp[User]
  case class GetValidatedGoogleTokenInfo(tokenValue: String, tokenType: TokenType) extends UserOp[GoogleTokenInfo]
}

object PendingFileOps {
  sealed trait PendingFileOp[A]

  case class SavePendingFile(pendingFile: PendingFile) extends PendingFileOp[PendingFile]
  case class FindPendingFileForUserId(userId: String)  extends PendingFileOp[List[PendingFile]]
  case class DeletePendingFileById(id: String)         extends PendingFileOp[Unit]
  case class DeleteAllPendingFiles()                   extends PendingFileOp[Unit]
}

object TokenOps {
  sealed trait TokenOp[A]

  case class GenerateUserToken(user: User)   extends TokenOp[OAuth2AccessTokenResponse]
  case class GeneratePathToken(path: String) extends TokenOp[OAuth2AccessTokenResponse]
}

object RandomOps {
  sealed trait RandomOp[A]

  case class GenerateGuid() extends RandomOp[String]
}
