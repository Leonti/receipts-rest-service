import java.io.File

import algebras._
import authentication.{OAuth2AccessTokenResponse, SubClaim}
import cats.Id
import cats.implicits._
import cats.data.WriterT
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

  sealed trait SideEffect
  final object PendingFileSaved extends  SideEffect
  final object PendingFileSubmitted extends  SideEffect
  final object PendingFileRemoved extends SideEffect
  final case class FileEntityAdded(fe: FileEntity) extends SideEffect
  final case class LocalFileRemoved(file: File) extends SideEffect
  final case class LocalFileStreamSaved(file: File) extends SideEffect

  type TestProgram[A] = WriterT[IO, List[SideEffect], A]

  private def wrapped[A](value: A): TestProgram[A] =
    WriterT[IO, List[SideEffect], A](IO.pure((List(), value)))
  private def wrapped[A](value: A, se: SideEffect): TestProgram[A] =
    WriterT[IO, List[SideEffect], A](IO.pure((List(se), value)))
  private def wrapped(se: SideEffect): TestProgram[Unit] =
    WriterT[IO, List[SideEffect], Unit](IO.pure((List(se), ())))

  val defaultUserId = "123-user"
  val defaultUsername = "123-username"
  val defaultExternalId = "externalId"
  val defaultUsers = List(UserIds(
    id = defaultUserId,
    username = defaultUsername,
    externalId = defaultExternalId))
  val defaultUserEmail = "email"

  class UserIntTest(users: List[UserIds] = defaultUsers, email: String = defaultUserEmail) extends UserAlg[TestProgram] {
    def findByUsername(username: String): TestProgram[List[UserIds]] = wrapped(users.filter(_.username == username))
    def findByExternalId(id: String): TestProgram[Option[UserIds]] = wrapped(users.find(_.externalId == id))
    def saveUserIds(userIds: UserIds): TestProgram[Unit] = wrapped(())
    def getExternalUserInfoFromAccessToken(accessToken: AccessToken): TestProgram[ExternalUserInfo] =
      wrapped(ExternalUserInfo(sub = "", email = email))
  }

  val defaultRandomId = "randomId"
  val defaultTime = 0
  val defaultTmpFile = new File("tmp-file")
  val defaultResizedFile = new File("resized")

  class RandomIntTest(id: String = defaultRandomId, time: Long = defaultTime, file: File = defaultTmpFile) extends RandomAlg[TestProgram] {
    override def generateGuid(): TestProgram[String] = wrapped(id)
    override def getTime(): TestProgram[Long]                = wrapped(time)
    override def tmpFile(): TestProgram[File]             = wrapped(file)
  }

  class RemoteFileIntTest extends RemoteFileAlg[TestProgram] {
    override def saveRemoteFile(file: File, fileId: RemoteFileId): TestProgram[Unit]        = wrapped(())
    override def deleteRemoteFile(fileId: RemoteFileId): TestProgram[Unit]                                 = wrapped(())
    override def remoteFileStream(fileId: RemoteFileId): TestProgram[Stream[TestProgram, Byte]] =
      wrapped(Stream.fromIterator[TestProgram, Byte]("some text".getBytes.toIterator))
  }

  class LocalFileIntTest(genericMetaData: GenericMetaData = GenericMetaData(length = 0)) extends LocalFileAlg[TestProgram] {
    override def getGenericMetaData(file: File): TestProgram[GenericMetaData] = wrapped(genericMetaData)
    override def getMd5(file: File): TestProgram[String] = wrapped("")
    override def moveFile(src: File, dst: File): TestProgram[Unit]        = wrapped(())
    override def removeFile(file: File): TestProgram[Unit]                                                      =
      wrapped(LocalFileRemoved(file))
    override def streamToFile(source: Stream[TestProgram, Byte], file: File): TestProgram[File] =
      wrapped(file, LocalFileStreamSaved(file))
  }

  class FileStoreIntTest(md5Response: List[StoredFile] = List()) extends FileStoreAlg[TestProgram] {
    override def saveStoredFile(storedFile: StoredFile): TestProgram[Unit] = wrapped(())
    override def findByMd5(userId: UserId,
                           md5: String): TestProgram[List[StoredFile]] = wrapped(md5Response)
    override def deleteStoredFile(userId: UserId, storedFileId: String): TestProgram[Unit]               = wrapped(())
  }

  class PendingFileIntTest extends PendingFileAlg[TestProgram] {
    override def savePendingFile(pendingFile: PendingFile): TestProgram[PendingFile]                   =
      wrapped(pendingFile, PendingFileSaved)
    override def findPendingFileForUserId(userId: UserId): TestProgram[List[PendingFile]] =
      wrapped(List())
    override def deletePendingFileById(userId: UserId, id: String): TestProgram[Unit] =
      wrapped(PendingFileRemoved)
  }

  class QueueIntTest extends QueueAlg[TestProgram] {
    override def submit(queueJob: QueueJob): TestProgram[Unit] =
      wrapped(PendingFileSubmitted)
    override def reserve(): TestProgram[Option[ReservedJob]] = wrapped(None)
    override def delete(id: String): TestProgram[Unit]              = wrapped(())
    override def release(id: String): TestProgram[Unit]             = wrapped(())
    override def bury(id: String): TestProgram[Unit]                = wrapped(())
  }

  class ReceiptStoreIntTest(
                                   receipts: List[ReceiptEntity] = List()) extends ReceiptStoreAlg[TestProgram] {
    override def getReceipt(userId: UserId,
                            id: String): TestProgram[Option[ReceiptEntity]] = wrapped(receipts.find(_.id == id))
    override def deleteReceipt(userId: UserId, id: String): TestProgram[Unit] = wrapped(())
    override def saveReceipt(receipt: ReceiptEntity): TestProgram[ReceiptEntity] = wrapped(receipt)
    override def getReceipts(userId: UserId,
                             ids: List[String]): TestProgram[List[ReceiptEntity]] = wrapped(receipts)
    override def userReceipts(userId: UserId): TestProgram[List[ReceiptEntity]] = wrapped(receipts)
    override def recentUserReceipts(userId: UserId,
                                    lastModified: Long): TestProgram[List[ReceiptEntity]] = wrapped(receipts)
    override def addFileToReceipt(userId: UserId, receiptId: String,
                                  file: FileEntity): TestProgram[Unit] =
      wrapped(FileEntityAdded(file))
  }

  class OcrIntTest() extends OcrAlg[TestProgram] {
    val testAnnotation = OcrTextAnnotation(text = "Parsed ocr text", pages = List())

    override def ocrImage(file: File): TestProgram[OcrTextAnnotation] = wrapped(testAnnotation)
    override def saveOcrResult(userId: String,
                               receiptId: String,
                               ocrResult: OcrTextAnnotation): TestProgram[Unit] = wrapped(())
    override def addOcrToIndex(userId: String,
                               receiptId: String,
                               ocrText: OcrText): TestProgram[Unit] = wrapped(())
    override def findIdsByText(userId: String,
                               query: String): TestProgram[List[String]] =
      wrapped(List[String]())
  }

  val defaultVerifyResult = Right(SubClaim(defaultExternalId))

  class JwtVerificationIntTest(result: Either[String, SubClaim] = defaultVerifyResult) extends JwtVerificationAlg[Id] {
    override def verify(token: String): Id[Either[String, SubClaim]] = result
  }

  class ImageIntTest(isImage: Boolean, imageMetaData: ImageMetaData = ImageMetaData(width = 1, height = 2, length = 3)) extends ImageAlg[TestProgram] {
    override def resizeToPixelSize(file: File, pixels: Long): TestProgram[File] = wrapped(defaultResizedFile)
    override def resizeToFileSize(file: File, sizeInMb: Double): TestProgram[File] = wrapped(defaultResizedFile)
    override def isImage(file: File): TestProgram[Boolean]                                        = wrapped(isImage)
    override def getImageMetaData(file: File): TestProgram[ImageMetaData]          = wrapped(imageMetaData)
  }

  val defaultPathToken = OAuth2AccessTokenResponse("", "", 10)

  val testAlgebras: RoutingAlgebras[TestProgram] = RoutingAlgebras(
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
