import java.io.ByteArrayInputStream
import java.util.zip.{ZipEntry, ZipInputStream}

import TestConfig._
import authentication.OAuth2AccessTokenResponse
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}
import cats.effect.IO
import org.http4s.client.blaze.Http1Client
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._

import scala.concurrent.ExecutionContext.Implicits.global

class BackupSpec extends FlatSpec with Matchers with ScalaFutures {
  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(120, Seconds), interval = Span(1000, Millis))

  private val httpClient = Http1Client[IO]().unsafeRunSync()
  val userTestUtils = new UserTestUtils(httpClient)
  val receiptTestUtils = new ReceiptTestUtils(httpClient)

  private def getBackupToken(accessToken: String): IO[OAuth2AccessTokenResponse] = {
    import org.http4s.circe.CirceEntityCodec._

    httpClient.expect[OAuth2AccessTokenResponse](
      GET(
        org.http4s.Uri.unsafeFromString(s"$appHostPort/backup/token"),
        org.http4s.headers.Authorization(Credentials.Token(AuthScheme.Bearer, accessToken))
      )
    )
  }

  private def getBackup(userId: String, backupToken: String): IO[Array[Byte]] = httpClient.expect[Array[Byte]](
    GET(org.http4s.Uri.unsafeFromString(s"$appHostPort/user/$userId/backup/download?access_token=$backupToken"))
  )

  it should "download a backup" in {

    val zipEntriesIO: IO[List[ZipEntry]] = for {
      r      <- userTestUtils.createUser
      (userInfo, accessToken) = r
      firstReceiptEntity <- receiptTestUtils.createReceipt(receiptTestUtils.createImageFileContent, accessToken.value)
      _ <- receiptTestUtils.getProcessedReceipt(firstReceiptEntity.id, accessToken.value)
      backupToken <- getBackupToken(accessToken.value)
      backupBytes <- getBackup(userInfo.id, backupToken.accessToken)
    } yield toZipEntries(backupBytes)

    whenReady(zipEntriesIO.unsafeToFuture()) { zipEntries: List[ZipEntry] =>
      zipEntries.length shouldBe 2
    }
  }

  def toZipEntries(bytes: Array[Byte]): List[ZipEntry] = {
    val zipStream  = new ZipInputStream(new ByteArrayInputStream(bytes))
    val zipEntries = Stream.continually(zipStream.getNextEntry).takeWhile(_ != null).toList
    zipStream.close()

    zipEntries
  }

}
