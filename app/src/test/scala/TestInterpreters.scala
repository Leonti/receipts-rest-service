import java.io.File

import algebras._
import authentication.{OAuth2AccessTokenResponse, SubClaim}
import cats.Id
import cats.effect.IO
import fs2.Stream
import model._
import ocr.{OcrText, OcrTextAnnotation}
import pending.PendingFile
import queue.{QueueJob, ReservedJob}
import receipt._
import routing.{RoutingAlgebras, RoutingConfig}
import user.{UserId, UserIds}

object TestInterpreters {

  val defaultUserId = "123-user"
  val defaultUsername = "123-username"
  val defaultExternalId = "externalId"
  val defaultUsers = List(UserIds(
    id = defaultUserId,
    username = defaultUsername,
    externalId = defaultExternalId))
  val defaultUserEmail = "email"

  class UserIntTest(users: List[UserIds] = defaultUsers, email: String = defaultUserEmail) extends UserAlg[IO] {
    def findByUsername(username: String): IO[List[UserIds]] = IO.pure(users.filter(_.username == username))
    def findByExternalId(id: String): IO[Option[UserIds]] = IO.pure(users.find(_.externalId == id))
    def saveUserIds(userIds: UserIds): IO[Unit] = IO.pure(())
    def getExternalUserInfoFromAccessToken(accessToken: AccessToken): IO[ExternalUserInfo] =
      IO.pure(ExternalUserInfo(sub = "", email = email))
  }

  val defaultRandomId = "randomId"
  val defaultTime = 0
  val defaultTmpFile = new File("")

  class RandomIntTest(id: String = defaultRandomId, time: Long = defaultTime, file: File = defaultTmpFile) extends RandomAlg[IO] {
    override def generateGuid(): IO[String] = IO.pure(id)
    override def getTime(): IO[Long]                = IO.pure(time)
    override def tmpFile(): IO[File]             = IO.pure(file)
  }

  class RemoteFileIntTest extends RemoteFileAlg[IO] {
    override def saveRemoteFile(file: File, fileId: RemoteFileId): IO[Unit]        = IO.pure(())
    override def deleteRemoteFile(fileId: RemoteFileId): IO[Unit]                                 = IO.pure(())
    override def remoteFileStream(fileId: RemoteFileId): IO[Stream[IO, Byte]] =
      IO.pure(Stream.fromIterator[IO, Byte]("some text".getBytes.toIterator))
  }

  class LocalFileIntTest extends LocalFileAlg[IO] {
    override def getFileMetaData(file: File): IO[FileMetaData] = IO.pure(GenericMetaData(length = 0))
    override def getMd5(file: File): IO[String] = IO.pure("")
    override def moveFile(src: File, dst: File): IO[Unit]        = IO.pure(())
    override def removeFile(file: File): IO[Unit]                                                      = IO.pure(())
    override def streamToFile(source: Stream[IO, Byte], file: File): IO[File] = IO.pure(file)
  }

  class FileStoreIntTest(md5Response: Seq[StoredFile] = List()) extends FileStoreAlg[IO] {
    override def saveStoredFile(storedFile: StoredFile): IO[Unit] = IO.pure(())
    override def findByMd5(userId: UserId,
                           md5: String): IO[Seq[StoredFile]] = IO.pure(md5Response)
    override def deleteStoredFile(userId: UserId, storedFileId: String): IO[Unit]               = IO.pure(())
  }

  class PendingFileIntTest extends PendingFileAlg[IO] {
    override def savePendingFile(pendingFile: PendingFile): IO[PendingFile]                   = IO.pure(pendingFile)
    override def findPendingFileForUserId(userId: UserId): IO[List[PendingFile]] = IO.pure(List())
    override def deletePendingFileById(userId: UserId, id: String): IO[Unit] = IO.pure(())
  }

  class QueueIntTest extends QueueAlg[IO] {
    override def submit(queueJob: QueueJob): IO[Unit] = IO.pure(())
    override def reserve(): IO[Option[ReservedJob]] = IO.pure(None)
    override def delete(id: String): IO[Unit]              = IO.pure(())
    override def release(id: String): IO[Unit]             = IO.pure(())
    override def bury(id: String): IO[Unit]                = IO.pure(())
  }

  class ReceiptStoreIntTest(
                                   receipts: Seq[ReceiptEntity] = List()) extends ReceiptStoreAlg[IO] {
    override def getReceipt(userId: UserId,
                            id: String): IO[Option[ReceiptEntity]] = IO.pure(receipts.find(_.id == id))
    override def deleteReceipt(userId: UserId, id: String): IO[Unit] = IO.pure(())
    override def saveReceipt(receipt: ReceiptEntity): IO[ReceiptEntity] = IO.pure(receipt)
    override def getReceipts(userId: UserId,
                             ids: Seq[String]): IO[Seq[ReceiptEntity]] = IO.pure(receipts)
    override def userReceipts(userId: UserId): IO[Seq[ReceiptEntity]] = IO.pure(receipts)
    override def addFileToReceipt(userId: UserId, receiptId: String,
                                  file: FileEntity): IO[Unit] = IO.pure(())
  }

  class OcrIntTest() extends OcrAlg[IO] {
    val testAnnotation = OcrTextAnnotation(text = "Parsed ocr text", pages = List())

    override def ocrImage(file: File): IO[OcrTextAnnotation] = IO.pure(testAnnotation)
    override def saveOcrResult(userId: String,
                               receiptId: String,
                               ocrResult: OcrTextAnnotation): IO[Unit] = IO.pure(())
    override def addOcrToIndex(userId: String,
                               receiptId: String,
                               ocrText: OcrText): IO[Unit] = IO.pure(())
    override def findIdsByText(userId: String,
                               query: String): IO[Seq[String]] =
      IO.pure(Seq())
  }

  val defaultVerifyResult = Right(SubClaim(defaultExternalId))

  class JwtVerificationIntTest(result: Either[String, SubClaim] = defaultVerifyResult) extends JwtVerificationAlg[Id] {
    override def verify(token: String): Id[Either[String, SubClaim]] = result
  }

  val defaultPathToken = OAuth2AccessTokenResponse("", "", 10)

  val testAlgebras: RoutingAlgebras[IO] = RoutingAlgebras(
    jwtVerificationAlg = new JwtVerificationIntTest(),
    userAlg = new UserIntTest(),
    randomAlg = new RandomIntTest(),
    receiptStoreAlg = new ReceiptStoreIntTest(),
    localFileAlg = new LocalFileIntTest(),
    remoteFileAlg = new RemoteFileIntTest(),
    fileStoreAlg = new FileStoreIntTest(),
    pendingFileAlg = new PendingFileIntTest(),
    queueAlg = new QueueIntTest(),
    ocrAlg = new OcrIntTest()
  )

  val authSecret = "secret".getBytes

  val testConfig = RoutingConfig("", "", authSecret)
}
