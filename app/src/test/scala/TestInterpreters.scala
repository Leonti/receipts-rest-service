import java.io.{ByteArrayInputStream, File}

import akka.stream.IOResult
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import algebras._
import authentication.OAuth2AccessTokenResponse
import cats.Id
import model._
import ocr.model.OcrTextAnnotation
import queue.Models.JobId
import service.JwtTokenGenerator

import scala.concurrent.Future

// TODO remove futures
object TestInterpreters {

  class UserInterpreterTagless(users: Seq[User], email: String) extends UserAlg[Future] {
    override def findUserByExternalId(id: String): Future[Option[User]] = Future.successful(users.find(_.id == id)) // FIXME fix user id
    override def findUserByUsername(
        username: String): Future[Option[User]] = Future.successful(users.find(_.userName == username))
    override def saveUser(user: User): Future[User] = Future.successful(user)
    override def getExternalUserInfoFromAccessToken(
        accessToken: AccessToken): Future[ExternalUserInfo] = Future.successful(ExternalUserInfo(sub = "", email = email))
  }

  class TokenInterpreterTagless(currentTimeMillis: Long, bearerTokenSecret: String) extends TokenAlg[Future] {
    override def generatePathToken(
        path: String): Future[OAuth2AccessTokenResponse] =
      Future.successful(JwtTokenGenerator.generatePathToken(path, currentTimeMillis, bearerTokenSecret.getBytes))
  }

  class RandomInterpreterTagless(id: String, time: Long = 0, file: File = new File("")) extends RandomAlg[Future] {
    override def generateGuid(): Future[String] = Future.successful(id)
    override def getTime(): Future[Long]                = Future.successful(time)
    override def tmpFile(): Future[File]             = Future.successful(file)
  }

  class RandomInterpreterId(id: String, time: Long = 0, file: File = new File("")) extends RandomAlg[Id] {
    override def generateGuid(): Id[String] = id
    override def getTime(): Id[Long]                = time
    override def tmpFile(): Id[File]             = file
  }

  class FileInterpreterTagless(md5Response: Seq[StoredFile] = List()) extends FileAlg[Future] {
    override def submitPendingFile(pendingFile: PendingFile): Future[PendingFile] = Future.successful(pendingFile)
    override def submitToFileQueue(userId: String,
                                   receiptId: String,
                                   file: File,
                                   fileExt: String,
                                   pendingFileId: String): Future[JobId] =
      Future.successful("")
    override def moveFile(src: File, dst: File): Future[Unit] = Future.successful((): Unit)
    override def saveFile(userId: String,
                          file: File,
                          ext: String): Future[Seq[FileEntity]] = Future.successful(List())
    override def saveStoredFile(storedFile: StoredFile): Future[Unit] = Future.successful(())
    override def findByMd5(userId: String,
                           md5: String): Future[Seq[StoredFile]] = Future.successful(md5Response)
    override def deleteStoredFile(storedFileId: String): Future[Unit] = Future.successful(())
    override def fetchFile(userId: String, fileId: String): Future[
      Source[ByteString, Future[IOResult]]] = Future.successful(StreamConverters.fromInputStream(() => new ByteArrayInputStream("some text".getBytes)))
    override def sourceToFile(
        source: Source[ByteString, Future[IOResult]],
        file: File): Future[File] = Future.successful(file)
    override def deleteFile(userId: String, fileId: String): Future[Unit] =
      Future.successful(())
    override def removeFile(file: File): Future[Unit]                         = Future.successful(())
    override def calculateMd5(file: File): Future[String] = Future.successful("")
  }

  class FileInterpreterId(md5Response: Seq[StoredFile] = List()) extends FileAlg[Id] {
    override def submitPendingFile(pendingFile: PendingFile): Id[PendingFile] = pendingFile
    override def submitToFileQueue(userId: String,
                                   receiptId: String,
                                   file: File,
                                   fileExt: String,
                                   pendingFileId: String): Id[JobId] = ""
    override def moveFile(src: File, dst: File): Id[Unit] = ()
    override def saveFile(userId: String,
                          file: File,
                          ext: String): Id[Seq[FileEntity]] = List()
    override def saveStoredFile(storedFile: StoredFile): Id[Unit] = ()
    override def findByMd5(userId: String,
                           md5: String): Id[Seq[StoredFile]] = md5Response
    override def deleteStoredFile(storedFileId: String): Id[Unit] = ()
    override def fetchFile(userId: String, fileId: String): Id[
      Source[ByteString, Future[IOResult]]] = StreamConverters.fromInputStream(() => new ByteArrayInputStream("some text".getBytes))
    override def sourceToFile(
                               source: Source[ByteString, Future[IOResult]],
                               file: File): Id[File] = file
    override def deleteFile(userId: String, fileId: String): Id[Unit] = ()
    override def removeFile(file: File): Id[Unit]                         = ()
    override def calculateMd5(file: File): Id[String] = ""
  }

  class ReceiptInterpreterTagless(
                            receipts: Seq[ReceiptEntity] = List(),
                            ocrs: Seq[OcrTextOnly] = List()
                          ) extends ReceiptAlg[Future] {
    override def getReceipt(userId: UserId,
                            id: String): Future[Option[ReceiptEntity]] = Future.successful(receipts.find(_.id == id))
    override def deleteReceipt(userId: UserId, id: String): Future[Unit] = Future.successful(())
    override def saveReceipt(userId: UserId, id: String,
                             receipt: ReceiptEntity): Future[ReceiptEntity] = Future.successful(receipt)
    override def getReceipts(userId: UserId,
                             ids: Seq[String]): Future[Seq[ReceiptEntity]] = Future.successful(receipts)
    override def userReceipts(userId: UserId): Future[Seq[ReceiptEntity]] = Future.successful(receipts)
    override def addFileToReceipt(userId: UserId, receiptId: String,
                                  file: FileEntity): Future[Unit] = Future.successful(())
  }

  class ReceiptInterpreterId(
                                   receipts: Seq[ReceiptEntity] = List(),
                                   ocrs: Seq[OcrTextOnly] = List()
                                 ) extends ReceiptAlg[Id] {
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

  class OcrInterpreterTagless() extends OcrAlg[Future] {
    val testAnnotation = OcrTextAnnotation(text = "Parsed ocr text", pages = List())

    override def ocrImage(file: File): Future[OcrTextAnnotation] = Future.successful(testAnnotation)
    override def saveOcrResult(userId: String,
                               receiptId: String,
                               ocrResult: OcrTextAnnotation): Future[OcrEntity] = Future.successful(OcrEntity(userId = userId, id = receiptId, result = testAnnotation))
    override def addOcrToIndex(userId: String,
                               receiptId: String,
                               ocrText: OcrText): Future[Unit] = Future.successful(())
    override def findIdsByText(userId: String,
                               query: String): Future[Seq[String]] =
      Future.successful(Seq())
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

  class PendingFileInterpreterTagless() extends PendingFileAlg[Future] {
    override def savePendingFile(pendingFile: PendingFile): Future[PendingFile] = Future.successful(pendingFile)
    override def findPendingFileForUserId(
        userId: String): Future[List[PendingFile]] = Future.successful(List())
    override def deletePendingFileById(id: String): Future[Unit] = Future.successful(())
    override def deleteAllPendingFiles(): Future[Unit] = Future.successful(())
  }

  class TestVerificationAlg(result: Either[String, SubClaim]) extends JwtVerificationAlg[Id] {
    override def verify(token: String): Id[Either[String, SubClaim]] = result
  }

}
