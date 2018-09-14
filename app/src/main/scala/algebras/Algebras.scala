package algebras

import java.io.{File, InputStream}
import authentication.OAuth2AccessTokenResponse
import com.twitter.io.Buf
import model._
import ocr.model.OcrTextAnnotation
import queue.Models.JobId
import scala.language.higherKinds

trait ReceiptAlg[F[_]] {
  def getReceipt(userId: UserId, id: String): F[Option[ReceiptEntity]]
  def deleteReceipt(userId: UserId, id: String): F[Unit]
  def saveReceipt(userId: UserId, id: String, receipt: ReceiptEntity): F[ReceiptEntity]
  def getReceipts(userId: UserId, ids: Seq[String]): F[Seq[ReceiptEntity]]
  def userReceipts(userId: UserId): F[Seq[ReceiptEntity]]
  def addFileToReceipt(userId: UserId, receiptId: String, file: FileEntity): F[Unit]
}

trait OcrAlg[F[_]] {
  def ocrImage(file: File): F[OcrTextAnnotation]
  def saveOcrResult(userId: String, receiptId: String, ocrResult: OcrTextAnnotation): F[OcrEntity]
  def addOcrToIndex(userId: String, receiptId: String, ocrText: OcrText): F[Unit]
  def findIdsByText(userId: String, query: String): F[Seq[String]]
}

trait RemoteFileAlg[F[_]] {
  def saveRemoteFile(file: File, fileId: RemoteFileId): F[Unit]
  def fetchRemoteFileInputStream(fileId: RemoteFileId): F[InputStream]
  def deleteRemoteFile(fileId: RemoteFileId): F[Unit]
}

trait LocalFileAlg[F[_]] {
  def getFileMetaData(file: File): F[FileMetaData]
  def moveFile(src: File, dst: File): F[Unit]
  def bufToFile(src: Buf, dst: File): F[Unit]
  def streamToFile(source: InputStream, file: File): F[File]
  def removeFile(file: File): F[Unit]
}

trait ImageResizeAlg[F[_]] {
  def resizeToPixelSize(file: File, pixels: Long): F[File]
  def resizeToFileSize(file: File, sizeInMb: Double): F[File]
}

trait FileStoreAlg[F[_]] {
  def saveStoredFile(storedFile: StoredFile): F[Unit]
  def findByMd5(userId: String, md5: String): F[Seq[StoredFile]]
  def deleteStoredFile(storedFileId: String): F[Unit]
}

trait QueueAlg[F[_]] {
  def submitToFileQueue(userId: String, receiptId: String, remoteFileId: RemoteFileId, fileExt: String, pendingFileId: String): F[JobId]
}

trait UserAlg[F[_]] {
  def findUserByUsername(username: String): F[Option[User]]
  def findUserByExternalId(id: String): F[Option[User]]
  def saveUser(user: User): F[User]
  def getExternalUserInfoFromAccessToken(accessToken: AccessToken): F[ExternalUserInfo]
}

trait PendingFileAlg[F[_]] {
  def savePendingFile(pendingFile: PendingFile): F[PendingFile]
  def findPendingFileForUserId(userId: String): F[List[PendingFile]]
  def deletePendingFileById(id: String): F[Unit]
  def deleteAllPendingFiles(): F[Unit]
}

trait TokenAlg[F[_]] {
  def generatePathToken(path: String): F[OAuth2AccessTokenResponse]
  def verifyPathToken(token: String): F[Either[String, SubClaim]]
}

trait EnvAlg[F[_]] {
  def getEnv(key: String): F[String]
}

trait RandomAlg[F[_]] {
  def generateGuid(): F[String]
  def getTime(): F[Long]
  def tmpFile(): F[File]
}

trait JwtVerificationAlg[F[_]] {
  def verify(token: String): F[Either[String, SubClaim]]
}
