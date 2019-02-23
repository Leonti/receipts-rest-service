import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import java.util.zip.{ZipEntry, ZipInputStream}

import TestInterpreters._
import authentication.{BearerAuth, OAuth2AccessTokenResponse}
import backup.{BackupEndpoints, BackupService}
import cats.effect.{ContextShift, IO}
import interpreters.TokenInterpreter
import io.finch.Input
import model._
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class BackupEndpointsSpec extends FlatSpec with Matchers {

  private val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  private implicit val cs: ContextShift[IO] = IO.contextShift(ec)

  val receiptInt = new ReceiptStoreInterpreterId(List())
  val remoteFileInt = new RemoteInterpreterId()
  val randomInt = new RandomInterpreterId("", 0)
  val ocrInt = new OcrInterpreterId()

  private val USER_ID = "123-user"
  val successfulAuth = new BearerAuth[IO, User](
    new TestVerificationAlg(Right(SubClaim(""))),
    _ => IO.pure(Some(User(USER_ID, "email", List())))
  ).auth

  it should "return the backup stream" in {
    val fileEntity =
      FileEntity(id = "1", parentId = None, ext = "txt", metaData = GenericMetaData(fileType = "TXT", length = 11), timestamp = 0l)
    val receipt = ReceiptEntity(id = "2", userId = "123-user", files = List(fileEntity))
    val tokenInt = new TokenInterpreter[IO]("secret".getBytes)

    val backupEndpoints = new BackupEndpoints[IO](
      successfulAuth,
      new BackupService[IO](new ReceiptStoreInterpreterId(List(receipt)), remoteFileInt),
      tokenInt
    )

    val accessToken: OAuth2AccessTokenResponse = tokenInt.generatePathToken(s"/user/$USER_ID/backup/download").unsafeRunSync()

    val input = Input.get(s"/user/$USER_ID/backup/download?access_token=${accessToken.accessToken}")

    val output = backupEndpoints.downloadBackup(input).awaitOutputUnsafe()

    output.flatMap(_.headers.get("Content-Type")) shouldBe Some("application/zip")
    output.map(o => toZipEntries(o.value.compile.toList.unsafeRunSync.toArray).size).get shouldBe 2
    output.map(o => StreamToString.streamToString(o.value)).get should include("receipts.json")
  }

  def toZipEntries(bytes: Array[Byte]): List[ZipEntry] = {
    val zipStream  = new ZipInputStream(new ByteArrayInputStream(bytes))
    val zipEntries = Stream.continually(zipStream.getNextEntry).takeWhile(_ != null).toList
    zipStream.close()

    zipEntries
  }

}
