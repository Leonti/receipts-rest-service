import java.nio.charset.Charset

import TestInterpreters._
import authentication.{BearerAuth, OAuth2AccessTokenResponse}
import cats.Id
import com.twitter.concurrent.AsyncStream
import com.twitter.io.Buf
import com.twitter.util.Await
import instances.identity._
import interpreters.TokenInterpreter
import io.finch.{Endpoint, Input}
import model._
import org.scalatest.{FlatSpec, Matchers}
import routing.BackupEndpoints
import service.BackupServiceIO

class BackupEndpointsSpec extends FlatSpec with Matchers {

  val receiptInt = new ReceiptInterpreterId(List())
  val fileInt = new FileInterpreterId()
  val randomInt = new RandomInterpreterId("", 0)
  val ocrInt = new OcrInterpreterId()

  private val USER_ID = "123-user"
  val successfulAuth: Endpoint[User] = new BearerAuth[Id, User](
    new TestVerificationAlg(Right(SubClaim(""))),
    _ => Some(User(USER_ID, "email", List()))
  ).auth

  val asyncStreamToString: AsyncStream[Buf] => String = as =>
    Await.result(as.toSeq().map(_.fold(Buf.Empty)((acc, a) => acc.concat(a))).map(buf => Buf.decodeString(buf, Charset.forName("UTF-8"))))


  it should "return the backup stream" in {
    val fileEntity =
      FileEntity(id = "1", parentId = None, ext = "txt", md5 = None, metaData = GenericMetadata(fileType = "TXT", length = 11))
    val receipt = ReceiptEntity(id = "2", userId = "123-user", files = List(fileEntity))
    val tokenInt = new TokenInterpreter[Id]()

    val backupEndpoints = new BackupEndpoints[Id](
      successfulAuth,
      new BackupServiceIO[Id](new ReceiptInterpreterId(List(receipt)), fileInt),
      tokenInt
    )

    val accessToken: OAuth2AccessTokenResponse = tokenInt.generatePathToken(s"/user/$USER_ID/backup/download")

    val input = Input.get(s"/user/$USER_ID/backup/download?access_token=${accessToken.accessToken}")

    val output = backupEndpoints.downloadBackup(input).awaitOutputUnsafe()
    output.flatMap(_.headers.get("Content-Type")) shouldBe Some("application/zip")
    output.map(o => asyncStreamToString(o.value)).get should include("receipts.json")
  }

}
