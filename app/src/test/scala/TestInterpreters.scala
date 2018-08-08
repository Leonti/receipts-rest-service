import java.io.{ByteArrayInputStream, File}

import akka.stream.IOResult
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import algebras._
import authentication.OAuth2AccessTokenResponse
import model._
import ocr.model.OcrTextAnnotation
import queue.Models.JobId
import service.{GoogleTokenInfo, JwtTokenGenerator, TokenType}

import scala.concurrent.Future

// TODO remove futures
object TestInterpreters {

  class UserInterpreterTagless(users: Seq[User], googleTokenEmail: String) extends UserAlg[Future] {
    override def findUserById(id: String): Future[Option[User]] = Future.successful(users.find(_.id == id))
    override def findUserByUsername(
        username: String): Future[Option[User]] = Future.successful(users.find(_.userName == username))
    override def saveUser(user: User): Future[User] = Future.successful(user)
    override def getValidatedGoogleTokenInfo(
        tokenValue: String,
        tokenType: TokenType): Future[GoogleTokenInfo] = Future.successful(GoogleTokenInfo(aud = "", sub = "", email = googleTokenEmail))
  }

  class TokenInterpreterTagless(currentTimeMillis: Long, bearerTokenSecret: String) extends TokenAlg[Future] {
    override def generateUserToken(
        user: User): Future[OAuth2AccessTokenResponse] = Future.successful(JwtTokenGenerator.generateToken(user, currentTimeMillis, bearerTokenSecret.getBytes))
    override def generatePathToken(
        path: String): Future[OAuth2AccessTokenResponse] =
      Future.successful(JwtTokenGenerator.generatePathToken(path, currentTimeMillis, bearerTokenSecret.getBytes))
  }

  class RandomInterpreterTagless(id: String, time: Long = 0, file: File = new File("")) extends RandomAlg[Future] {
    override def generateGuid(): Future[String] = Future.successful(id)
    override def getTime(): Future[Long]                = Future.successful(time)
    override def tmpFile(): Future[File]             = Future.successful(file)
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

  class ReceiptInterpreterTagless(
                            receipts: Seq[ReceiptEntity] = List(),
                            ocrs: Seq[OcrTextOnly] = List()
                          ) extends ReceiptAlg[Future] {
    override def getReceipt(
        id: String): Future[Option[ReceiptEntity]] = Future.successful(receipts.find(_.id == id))
    override def deleteReceipt(id: String): Future[Unit] = Future.successful(())
    override def saveReceipt(id: String,
                             receipt: ReceiptEntity): Future[ReceiptEntity] = Future.successful(receipt)
    override def getReceipts(
        ids: Seq[String]): Future[Seq[ReceiptEntity]] = Future.successful(receipts)
    override def userReceipts(userId: String): Future[Seq[ReceiptEntity]] = Future.successful(receipts)
    override def addFileToReceipt(receiptId: String,
                                  file: FileEntity): Future[Unit] = Future.successful(())
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

  class PendingFileInterpreterTagless() extends PendingFileAlg[Future] {
    override def savePendingFile(pendingFile: PendingFile): Future[PendingFile] = Future.successful(pendingFile)
    override def findPendingFileForUserId(
        userId: String): Future[List[PendingFile]] = Future.successful(List())
    override def deletePendingFileById(id: String): Future[Unit] = Future.successful(())
    override def deleteAllPendingFiles(): Future[Unit] = Future.successful(())
  }

}
