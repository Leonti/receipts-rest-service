import java.io.{ByteArrayInputStream, File, InputStream}

import algebras._
import cats.Id
import com.twitter.io.Buf
import model._
import ocr.model.OcrTextAnnotation
import queue.Models.JobId

object TestInterpreters {

  class UserInterpreterId(users: Seq[User], email: String) extends UserAlg[Id] {
    override def findUserByExternalId(id: String): Option[User] = users.find(_.externalIds.contains(id))
    override def findUserByUsername(
                                     username: String): Option[User] = users.find(_.userName == username)
    override def saveUser(user: User): User = user
    override def getExternalUserInfoFromAccessToken(
                                                     accessToken: AccessToken): ExternalUserInfo = ExternalUserInfo(sub = "", email = email)
  }

  class RandomInterpreterId(id: String, time: Long = 0, file: File = new File("")) extends RandomAlg[Id] {
    override def generateGuid(): Id[String] = id
    override def getTime(): Id[Long]                = time
    override def tmpFile(): Id[File]             = file
  }

  class RemoteInterpreterId extends RemoteFileAlg[Id] {
    override def saveRemoteFile(file: File, fileId: RemoteFileId): Id[Unit]        = ()
    override def fetchRemoteFileInputStream(fileId: RemoteFileId): Id[InputStream] = new ByteArrayInputStream("some text".getBytes)
    override def deleteRemoteFile(fileId: RemoteFileId): Id[Unit]                                 = ()
  }

  class LocalFileId extends LocalFileAlg[Id] {
    override def getFileMetaData(file: File): Id[FileMetaData] = GenericMetaData(md5 = "", length = 0)
    override def moveFile(src: File, dst: File): Id[Unit]        = ()
    override def bufToFile(src: Buf, dst: File): Id[Unit]                        = ()
    override def streamToFile(source: InputStream, file: File): Id[File] = file
    override def removeFile(file: File): Id[Unit]                                                      = ()
  }

  class FileStoreId(md5Response: Seq[StoredFile] = List()) extends FileStoreAlg[Id] {
    override def saveStoredFile(storedFile: StoredFile): Id[Unit] = ()
    override def findByMd5(userId: String,
                           md5: String): Id[Seq[StoredFile]] = md5Response
    override def deleteStoredFile(storedFileId: String): Id[Unit]               = ()
  }

  class PendingFileInterpreterId extends PendingFileAlg[Id] {
    override def savePendingFile(pendingFile: PendingFile): Id[PendingFile]                   = pendingFile
    override def findPendingFileForUserId(userId: String): Id[List[PendingFile]] = List()
    override def deletePendingFileById(id: String): Id[Unit] = ()
    override def deleteAllPendingFiles(): Id[Unit]                                                                      = ()
  }

  class QueueInterpreterId extends QueueAlg[Id] {
    override def submitToFileQueue(userId: String,
                                   receiptId: String,
                                   remoteFileId: RemoteFileId,
                                   fileExt: String,
                                   pendingFileId: String): Id[JobId] = ""
  }

  class ReceiptInterpreterId(
                                   receipts: Seq[ReceiptEntity] = List()) extends ReceiptAlg[Id] {
    override def getReceipt(userId: UserId,
                            id: String): Id[Option[ReceiptEntity]] = receipts.find(_.id == id)
    override def deleteReceipt(userId: UserId, id: String): Id[Unit] = ()
    override def saveReceipt(userId: UserId, id: String,
                             receipt: ReceiptEntity): Id[ReceiptEntity] = receipt
    override def getReceipts(userId: UserId,
                             ids: Seq[String]): Id[Seq[ReceiptEntity]] = receipts
    override def userReceipts(userId: UserId): Id[Seq[ReceiptEntity]] = receipts
    override def addFileToReceipt(userId: UserId, receiptId: String,
                                  file: FileEntity): Id[Unit] = ()
  }

  class OcrInterpreterId() extends OcrAlg[Id] {
    val testAnnotation = OcrTextAnnotation(text = "Parsed ocr text", pages = List())

    override def ocrImage(file: File): Id[OcrTextAnnotation] = testAnnotation
    override def saveOcrResult(userId: String,
                               receiptId: String,
                               ocrResult: OcrTextAnnotation): Id[OcrEntity] = OcrEntity(userId = userId, id = receiptId, result = testAnnotation)
    override def addOcrToIndex(userId: String,
                               receiptId: String,
                               ocrText: OcrText): Id[Unit] = ()
    override def findIdsByText(userId: String,
                               query: String): Id[Seq[String]] =
      Seq()
  }

  class TestVerificationAlg(result: Either[String, SubClaim]) extends JwtVerificationAlg[Id] {
    override def verify(token: String): Id[Either[String, SubClaim]] = result
  }

}
