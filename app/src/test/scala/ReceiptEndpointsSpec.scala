import TestInterpreters._
import authentication.BearerAuth
import cats.effect.IO
import com.twitter.finagle.http.{FileElement, RequestBuilder, Status}
import com.twitter.io.Buf
import com.twitter.io.Buf.ByteArray.Owned
import io.finch.{Application, Input}
import io.finch.circe._
import model._
import org.scalatest.{FlatSpec, Matchers}
import routing.ReceiptEndpoints
import service.{FileUploadPrograms, ReceiptPrograms}
class ReceiptEndpointsSpec extends FlatSpec with Matchers {

  val receiptInt = new ReceiptStoreInterpreterId(List())
  val localFile = new LocalFileId()
  val remoteFile = new RemoteInterpreterId()
  val fileStore = new FileStoreId()
  val pendingFile = new PendingFileInterpreterId()
  val queueInt = new QueueInterpreterId()
  val randomInt = new RandomInterpreterId("", 0)
  val ocrInt = new OcrInterpreterId()

  private val USER_ID = "123-user"
  val successfulAuth = new BearerAuth[IO, User](
    new TestVerificationAlg(Right(SubClaim(""))),
    _ => IO.pure(Some(User(USER_ID, "email", List())))
  ).auth

  it should "create receipt from file upload" in {
    val receiptRouting = new ReceiptEndpoints[IO](
      successfulAuth,
      new ReceiptPrograms(receiptInt, localFile, remoteFile, fileStore, pendingFile, queueInt, randomInt, ocrInt),
      new FileUploadPrograms("", localFile, randomInt)
    )

    val request = RequestBuilder().url("http://for.builder")
      .add(FileElement(
        name = "receipt",
        content = Owned("file content".getBytes),
        contentType = Some("application/octet-stream"),
        filename = Some("receipt.png")
      ))
      .addFormElement(
        "total" -> "12.38",
        "description" -> "some description",
        "transactionTime" -> "1480130712396",
        "tags" -> "veggies,food"
      ).addHeader("Authorization", "Bearer token")
      .buildFormPost(multipart = true)
      .uri("/receipt")
    val input = Input.fromRequest(request)

    receiptRouting.createReceipt(input).awaitValueUnsafe() shouldBe Some(ReceiptEntity(
      id = "",
      userId = USER_ID,
      total = Some(BigDecimal(12.38)),
      description = "some description",
      timestamp = 0,
      lastModified = 0,
      transactionTime = 1480130712396l,
      tags = List("veggies", "food"),
      files = List(FileEntity("", None, "png", GenericMetaData(length = 0), timestamp = 0))
    ))
  }

  it should "reject receipt if file already exists" in {
    val receiptRouting = new ReceiptEndpoints[IO](
      successfulAuth,
      new ReceiptPrograms(receiptInt, localFile, remoteFile, new FileStoreId(md5Response = List(StoredFile("123-user", "fileId", "md5"))), pendingFile, queueInt, randomInt, ocrInt),
      new FileUploadPrograms("", localFile, randomInt)
    )

    val request = RequestBuilder().url("http://for.builder")
      .add(FileElement(
        name = "receipt",
        content = Owned("file content".getBytes),
        contentType = Some("application/octet-stream"),
        filename = Some("receipt.png")
      ))
      .addFormElement(
        "total" -> "12.38",
        "description" -> "some description",
        "transactionTime" -> "1480130712396",
        "tags" -> "veggies,food"
      ).addHeader("Authorization", "Bearer token")
      .buildFormPost(multipart = true)
      .uri("/receipt")
    val input = Input.fromRequest(request)

    val result = receiptRouting.createReceipt(input).awaitOutputUnsafe()

    result.map(_.status) shouldBe Some(Status.BadRequest)
  }

  it should "reject receipt from file upload if form field is not present" in {
    val receiptRouting = new ReceiptEndpoints[IO](
      successfulAuth,
      new ReceiptPrograms(receiptInt, localFile, remoteFile, fileStore, pendingFile, queueInt, randomInt, ocrInt),
      new FileUploadPrograms("", localFile, randomInt)
    )

    val request = RequestBuilder().url("http://for.builder")
      .addFormElement(
        "total" -> "12.38",
        "description" -> "some description",
        "transactionTime" -> "1480130712396",
        "tags" -> "veggies,food"
      ).addHeader("Authorization", "Bearer token")
      .buildFormPost(multipart = true)
      .uri("/receipt")
    val input = Input.fromRequest(request)

    val result = receiptRouting.createReceipt(input).awaitOutputUnsafe()
    result.map(_.status) shouldBe Some(Status.BadRequest)
  }

  it should "return a file content" in {
    val fileEntity =
      FileEntity(id = "1", parentId = None, ext = "txt", metaData = GenericMetaData(fileType = "TXT", length = 11), timestamp = 0l)
    val receipt = ReceiptEntity(id = "2", userId = "123-user", files = List(fileEntity))
    val receiptRouting = new ReceiptEndpoints[IO](
      successfulAuth,
      new ReceiptPrograms(new ReceiptStoreInterpreterId(List(receipt)), localFile, remoteFile, new FileStoreId(md5Response = List(StoredFile("123-user", "fileId", "md5"))), pendingFile, queueInt, randomInt, ocrInt),
      new FileUploadPrograms("", localFile, randomInt)
    )

    val input = Input.get(s"/receipt/2/file/${fileEntity.id}.txt").withHeaders("Authorization" -> "Bearer token")

    val output = receiptRouting.getReceiptFile(input).awaitOutputUnsafe()
    output.flatMap(_.headers.get("Content-Type")) shouldBe Some("text/plain")
    output.map(o => StreamToString.streamToString(o.value)) shouldBe Some("some text")
  }

  it should "read receipt by id" in {
    val receipt = ReceiptEntity(id = "2", userId = USER_ID, files = List(), description = "some description")
    val receiptRouting = new ReceiptEndpoints[IO](
      successfulAuth,
      new ReceiptPrograms(new ReceiptStoreInterpreterId(List(receipt)), localFile, remoteFile, new FileStoreId(md5Response = List(StoredFile("123-user", "fileId", "md5"))), pendingFile, queueInt, randomInt, ocrInt),
      new FileUploadPrograms("", localFile, randomInt)
    )

    val input = Input.get("/receipt/2").withHeaders("Authorization" -> "Bearer token")
    receiptRouting.getReceipt(input).awaitValueUnsafe() shouldBe Some(receipt)
  }

  it should "patch a receipt" in {
    val receipt = ReceiptEntity(id = "1", userId = "123-user", files = List(), description = "some description")
    val receiptRouting = new ReceiptEndpoints[IO](
      successfulAuth,
      new ReceiptPrograms(new ReceiptStoreInterpreterId(List(receipt)), localFile, remoteFile, new FileStoreId(md5Response = List(StoredFile("123-user", "fileId", "md5"))), pendingFile, queueInt, randomInt, ocrInt),
      new FileUploadPrograms("", localFile, randomInt)
    )

    val patch = """[
                  |  {
                  |    "op": "replace",
                  |    "path": "/description",
                  |    "value": "some new description"
                  |  },
                  |  {
                  |    "op": "replace",
                  |    "path": "/total",
                  |    "value": 12.38
                  |  }
                  |]""".stripMargin

    val input = Input.patch(s"/receipt/${receipt.id}")
      .withHeaders("Authorization" -> "Bearer token")
      .withBody[Application.Json](Buf.Utf8(patch))

    val result = receiptRouting.patchReceipt(input).awaitValueUnsafe()
    result.map(_.description) shouldBe Some("some new description")
    result.flatMap(_.total) shouldBe Some(BigDecimal("12.38"))
  }

  it should "unset total after patch with null" in {
    val receipt = ReceiptEntity(userId = "123-user", files = List(), description = "some description", total = Some(BigDecimal("12.38")))
    val receiptRouting = new ReceiptEndpoints[IO](
      successfulAuth,
      new ReceiptPrograms(new ReceiptStoreInterpreterId(List(receipt)), localFile, remoteFile, new FileStoreId(md5Response = List(StoredFile("123-user", "fileId", "md5"))), pendingFile, queueInt, randomInt, ocrInt),
      new FileUploadPrograms("", localFile, randomInt)
    )

    val patch = """[
                  |  {
                  |    "op": "replace",
                  |    "path": "/total",
                  |    "value": null
                  |  }
                  |]""".stripMargin

    val input = Input.patch(s"/receipt/${receipt.id}")
      .withHeaders("Authorization" -> "Bearer token")
      .withBody[Application.Json](Buf.Utf8(patch))

    val result = receiptRouting.patchReceipt(input).awaitValueUnsafe()
    result.flatMap(_.total) shouldBe None
  }

  it should "unset total after patch with remove" in {
    val receipt = ReceiptEntity(userId = "123-user", files = List(), description = "some description", total = Some(BigDecimal("12.38")))
    val receiptRouting = new ReceiptEndpoints[IO](
      successfulAuth,
      new ReceiptPrograms(new ReceiptStoreInterpreterId(List(receipt)), localFile, remoteFile, new FileStoreId(md5Response = List(StoredFile("123-user", "fileId", "md5"))), pendingFile, queueInt, randomInt, ocrInt),
      new FileUploadPrograms("", localFile, randomInt)
    )

    val patch = """[
                  |  {
                  |    "op": "remove",
                  |    "path": "/total"
                  |  }
                  |]""".stripMargin

    val input = Input.patch(s"/receipt/${receipt.id}")
      .withHeaders("Authorization" -> "Bearer token")
      .withBody[Application.Json](Buf.Utf8(patch))

    val result = receiptRouting.patchReceipt(input).awaitValueUnsafe()
    result.flatMap(_.total) shouldBe None
  }

  it should "set tags with a patch" in {
    val receipt = ReceiptEntity(userId = "123-user", files = List(), description = "some description", total = Some(BigDecimal("12.38")))
    val receiptRouting = new ReceiptEndpoints[IO](
      successfulAuth,
      new ReceiptPrograms(new ReceiptStoreInterpreterId(List(receipt)), localFile, remoteFile, new FileStoreId(md5Response = List(StoredFile("123-user", "fileId", "md5"))), pendingFile, queueInt, randomInt, ocrInt),
      new FileUploadPrograms("", localFile, randomInt)
    )

    val patch = """[
                  |  {
                  |    "op": "replace",
                  |    "path": "/tags",
                  |    "value": ["vegetables", "food"]
                  |  }
                  |]""".stripMargin

    val input = Input.patch(s"/receipt/${receipt.id}")
      .withHeaders("Authorization" -> "Bearer token")
      .withBody[Application.Json](Buf.Utf8(patch))

    val result = receiptRouting.patchReceipt(input).awaitValueUnsafe()
    result.map(_.tags) shouldBe Some(List("vegetables", "food"))
  }

  it should "delete a receipt" in {
    val fileEntity =
      FileEntity(id = "1", parentId = None, ext = "txt", metaData = GenericMetaData(fileType = "TXT", length = 11), timestamp = 0l)
    val receipt = ReceiptEntity(id = "2", userId = "123-user", files = List(fileEntity))
    val receiptRouting = new ReceiptEndpoints[IO](
      successfulAuth,
      new ReceiptPrograms(new ReceiptStoreInterpreterId(List(receipt)), localFile, remoteFile, new FileStoreId(md5Response = List(StoredFile("123-user", "fileId", "md5"))), pendingFile, queueInt, randomInt, ocrInt),
      new FileUploadPrograms("", localFile, randomInt)
    )

    val input = Input.delete(s"/receipt/${receipt.id}")
      .withHeaders("Authorization" -> "Bearer token")

    val result = receiptRouting.deleteReceipt(input).awaitOutputUnsafe()

    result.map(_.status) shouldBe Some(Status.NoContent)
  }

  it should "return list of receipts" in {
    val receipt = ReceiptEntity(userId = "123-user", files = List(), description = "some description", total = Some(BigDecimal("12.38")))
    val receiptRouting = new ReceiptEndpoints[IO](
      successfulAuth,
      new ReceiptPrograms(new ReceiptStoreInterpreterId(List(receipt)), localFile, remoteFile, new FileStoreId(md5Response = List(StoredFile("123-user", "fileId", "md5"))), pendingFile, queueInt, randomInt, ocrInt),
      new FileUploadPrograms("", localFile, randomInt)
    )

    val input = Input.get("/receipt").withHeaders("Authorization" -> "Bearer token")

    receiptRouting.getReceipts(input).awaitValueUnsafe() shouldBe Some(Seq(receipt))
  }

}
