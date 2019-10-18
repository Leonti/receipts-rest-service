import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.{ZipEntry, ZipInputStream}

import TestInterpreters.{TestProgram, _}
import authentication.PathToken
import cats.implicits._
import cats.effect.{ContextShift, IO}
import org.http4s.headers.`Content-Type`
import org.http4s._
import org.scalatest.{FlatSpec, Matchers}
import receipt.{FileEntity, GenericMetaData, ReceiptEntity, StoredFile}
import routing.Routing

import scala.concurrent.ExecutionContext.Implicits.global

class BackupEndpointsSpec extends FlatSpec with Matchers {

  private implicit val cs: ContextShift[IO] = IO.contextShift(global)

  it should "return the backup stream" in {
    val fileEntity =
      FileEntity(id = "1", parentId = None, ext = "txt", metaData = GenericMetaData(fileType = "TXT", length = 11), timestamp = 0L)
    val receipt = ReceiptEntity(id = "2", userId = defaultUserId, files = List(fileEntity))

    val routing = new Routing(testAlgebras.copy(
      receiptStoreAlg = new ReceiptStoreIntTest(List(receipt)),
      fileStoreAlg = new FileStoreIntTest(md5Response = List(StoredFile(defaultUserId, "fileId", "md5")))), testConfig, global)

    val accessToken = new PathToken(authSecret).generatePathToken(s"/user/$defaultUserId/backup/download")

    val request: Request[TestProgram] = Request(
      method = Method.GET,
      uri = Uri.unsafeFromString(s"/user/$defaultUserId/backup/download?access_token=${accessToken.accessToken}")
    )

    val (_, response) = routing.routes.run(request).value.run.unsafeRunSync()
    val content = response.map(res => res.body.compile.toList.run.unsafeRunSync._2.toArray)
    val contentType = response.flatMap(_.headers.get(`Content-Type`).map(_.value))

    contentType shouldBe Some("application/zip")
    content.map(toZipEntries).map(_.size) shouldBe Some(2)
    content.map(bytes => new String(bytes, StandardCharsets.UTF_8)).get should include("receipts.json")
  }

  def toZipEntries(bytes: Array[Byte]): List[ZipEntry] = {
    val zipStream  = new ZipInputStream(new ByteArrayInputStream(bytes))
    val zipEntries = LazyList.continually(zipStream.getNextEntry).takeWhile(_ != null).toList
    zipStream.close()

    zipEntries
  }

}
