package algebras

import java.io.{File, InputStream}

import akka.stream.IOResult
import akka.stream.scaladsl.Source
import akka.util.ByteString
import authentication.OAuth2AccessTokenResponse
import com.twitter.io.Buf
import model._
import ocr.model.OcrTextAnnotation
import queue.Models.JobId
import fs2.Stream

import scala.concurrent.Future
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

trait FileAlg[F[_]] {
  def submitPendingFile(pendingFile: PendingFile): F[PendingFile]
  def submitToFileQueue(userId: String, receiptId: String, file: File, fileExt: String, pendingFileId: String): F[JobId]
  def moveFile(src: File, dst: File): F[Unit]
  def bufToFile(src: Buf, dst: File): F[Unit]
  def saveFile(userId: String, file: File, ext: String): F[Seq[FileEntity]]
  def saveStoredFile(storedFile: StoredFile): F[Unit]
  def findByMd5(userId: String, md5: String): F[Seq[StoredFile]]
  def deleteStoredFile(storedFileId: String): F[Unit]
  def fetchFile(userId: String, fileId: String): F[Source[ByteString, Future[IOResult]]]
  def fetchFileInputStream(userId: String, fileId: String): F[InputStream]
  def sourceToFile(source: Source[ByteString, Future[IOResult]], file: File): F[File]
  def fs2StreamToFile(source: Stream[F, Byte], file: File): F[File]
  def deleteFile(userId: String, fileId: String): F[Unit]
  def removeFile(file: File): F[Unit]
  def calculateMd5(file: File): F[String]
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
