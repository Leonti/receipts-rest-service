package algebras

import java.io.File

import authentication.SubClaim
import model._
import queue.Models.JobId
import fs2.Stream
import ocr.{OcrText, OcrTextAnnotation}
import pending.PendingFile
import receipt._
import user.{UserId, UserIds}

import scala.language.higherKinds

trait ReceiptStoreAlg[F[_]] {
  def getReceipt(userId: UserId, id: String): F[Option[ReceiptEntity]]
  def deleteReceipt(userId: UserId, id: String): F[Unit]
  def saveReceipt(receipt: ReceiptEntity): F[ReceiptEntity]
  def getReceipts(userId: UserId, ids: Seq[String]): F[Seq[ReceiptEntity]]
  def userReceipts(userId: UserId): F[Seq[ReceiptEntity]]
  def addFileToReceipt(userId: UserId, receiptId: String, file: FileEntity): F[Unit]
}

trait OcrAlg[F[_]] {
  def ocrImage(file: File): F[OcrTextAnnotation]
  def saveOcrResult(userId: String, receiptId: String, ocrResult: OcrTextAnnotation): F[Unit]
  def addOcrToIndex(userId: String, receiptId: String, ocrText: OcrText): F[Unit]
  def findIdsByText(userId: String, query: String): F[Seq[String]]
}

trait RemoteFileAlg[F[_]] {
  def saveRemoteFile(file: File, fileId: RemoteFileId): F[Unit]
  def remoteFileStream(fileId: RemoteFileId): F[Stream[F, Byte]]
  def deleteRemoteFile(fileId: RemoteFileId): F[Unit]
}

trait LocalFileAlg[F[_]] {
  def getFileMetaData(file: File): F[FileMetaData]
  def getMd5(file: File): F[String]
  def moveFile(src: File, dst: File): F[Unit]
  def streamToFile(source: Stream[F, Byte], file: File): F[File]
  def removeFile(file: File): F[Unit]
}

trait ImageResizeAlg[F[_]] {
  def resizeToPixelSize(file: File, pixels: Long): F[File]
  def resizeToFileSize(file: File, sizeInMb: Double): F[File]
}

trait FileStoreAlg[F[_]] {
  def saveStoredFile(storedFile: StoredFile): F[Unit]
  def findByMd5(userId: UserId, md5: String): F[Seq[StoredFile]]
  def deleteStoredFile(userId: UserId, id: String): F[Unit]
}

trait QueueAlg[F[_]] {
  def submitToFileQueue(userId: String, receiptId: String, remoteFileId: RemoteFileId, fileExt: String, pendingFileId: String): F[JobId]
}

trait UserAlg[F[_]] {
  def findByUsername(username: String): F[List[UserIds]]
  def findByExternalId(id: String): F[Option[UserIds]]
  def saveUserIds(userIds: UserIds): F[Unit]
  def getExternalUserInfoFromAccessToken(accessToken: AccessToken): F[ExternalUserInfo]
}

trait PendingFileAlg[F[_]] {
  def savePendingFile(pendingFile: PendingFile): F[PendingFile]
  def findPendingFileForUserId(userId: UserId): F[List[PendingFile]]
  def deletePendingFileById(userId: UserId, id: String): F[Unit]
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
