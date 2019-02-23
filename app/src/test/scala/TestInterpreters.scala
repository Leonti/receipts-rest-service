import java.io.File

import algebras._
import cats.Id
import cats.effect.IO
import fs2.Stream
import com.twitter.io.Buf
import model._
import ocr.model.OcrTextAnnotation
import queue.Models.JobId

object TestInterpreters {

  class UserInterpreterId(users: Seq[User], email: String) extends UserAlg[IO] {
    override def findUserByExternalId(id: String): IO[Option[User]] = IO.pure(users.find(_.externalIds.contains(id)))
    override def findUserByUsername(
                                     username: String): IO[Option[User]] = IO.pure(users.find(_.userName == username))
    override def saveUser(user: User): IO[User] = IO.pure(user)
    override def getExternalUserInfoFromAccessToken(
                                                     accessToken: AccessToken): IO[ExternalUserInfo] = IO.pure(ExternalUserInfo(sub = "", email = email))
  }

  class RandomInterpreterId(id: String, time: Long = 0, file: File = new File("")) extends RandomAlg[IO] {
    override def generateGuid(): IO[String] = IO.pure(id)
    override def getTime(): IO[Long]                = IO.pure(time)
    override def tmpFile(): IO[File]             = IO.pure(file)
  }

  class RemoteInterpreterId extends RemoteFileAlg[IO] {
    override def saveRemoteFile(file: File, fileId: RemoteFileId): IO[Unit]        = IO.pure(())
    override def deleteRemoteFile(fileId: RemoteFileId): IO[Unit]                                 = IO.pure(())
    override def remoteFileStream(fileId: RemoteFileId): IO[Stream[IO, Byte]] =
      IO.pure(Stream.fromIterator[IO, Byte]("some text".getBytes.toIterator))
  }

  class LocalFileId extends LocalFileAlg[IO] {
    override def getFileMetaData(file: File): IO[FileMetaData] = IO.pure(GenericMetaData(length = 0))
    override def getMd5(file: File): IO[String] = IO.pure("")
    override def moveFile(src: File, dst: File): IO[Unit]        = IO.pure(())
    override def bufToFile(src: Buf, dst: File): IO[Unit]                        = IO.pure(())
    override def removeFile(file: File): IO[Unit]                                                      = IO.pure(())
    override def streamToFile(source: Stream[IO, Byte], file: File): IO[File] = IO.pure(file)
  }

  class FileStoreId(md5Response: Seq[StoredFile] = List()) extends FileStoreAlg[IO] {
    override def saveStoredFile(storedFile: StoredFile): IO[Unit] = IO.pure(())
    override def findByMd5(userId: String,
                           md5: String): IO[Seq[StoredFile]] = IO.pure(md5Response)
    override def deleteStoredFile(storedFileId: String): IO[Unit]               = IO.pure(())
  }

  class PendingFileInterpreterId extends PendingFileAlg[IO] {
    override def savePendingFile(pendingFile: PendingFile): IO[PendingFile]                   = IO.pure(pendingFile)
    override def findPendingFileForUserId(userId: String): IO[List[PendingFile]] = IO.pure(List())
    override def deletePendingFileById(id: String): IO[Unit] = IO.pure(())
    override def deleteAllPendingFiles(): IO[Unit]                                                                      = IO.pure(())
  }

  class QueueInterpreterId extends QueueAlg[IO] {
    override def submitToFileQueue(userId: String,
                                   receiptId: String,
                                   remoteFileId: RemoteFileId,
                                   fileExt: String,
                                   pendingFileId: String): IO[JobId] = IO.pure("")
  }

  class ReceiptStoreInterpreterId(
                                   receipts: Seq[ReceiptEntity] = List()) extends ReceiptStoreAlg[IO] {
    override def getReceipt(userId: UserId,
                            id: String): IO[Option[ReceiptEntity]] = IO.pure(receipts.find(_.id == id))
    override def deleteReceipt(userId: UserId, id: String): IO[Unit] = IO.pure(())
    override def saveReceipt(userId: UserId, id: String,
                             receipt: ReceiptEntity): IO[ReceiptEntity] = IO.pure(receipt)
    override def getReceipts(userId: UserId,
                             ids: Seq[String]): IO[Seq[ReceiptEntity]] = IO.pure(receipts)
    override def userReceipts(userId: UserId): IO[Seq[ReceiptEntity]] = IO.pure(receipts)
    override def addFileToReceipt(userId: UserId, receiptId: String,
                                  file: FileEntity): IO[Unit] = IO.pure(())
  }

  class OcrInterpreterId() extends OcrAlg[IO] {
    val testAnnotation = OcrTextAnnotation(text = "Parsed ocr text", pages = List())

    override def ocrImage(file: File): IO[OcrTextAnnotation] = IO.pure(testAnnotation)
    override def saveOcrResult(userId: String,
                               receiptId: String,
                               ocrResult: OcrTextAnnotation): IO[OcrEntity] = IO.pure(OcrEntity(userId = userId, id = receiptId, result = testAnnotation))
    override def addOcrToIndex(userId: String,
                               receiptId: String,
                               ocrText: OcrText): IO[Unit] = IO.pure(())
    override def findIdsByText(userId: String,
                               query: String): IO[Seq[String]] =
      IO.pure(Seq())
  }

  class TestVerificationAlg(result: Either[String, SubClaim]) extends JwtVerificationAlg[Id] {
    override def verify(token: String): Id[Either[String, SubClaim]] = result
  }

}
