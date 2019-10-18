import TestInterpreters._
import cats.effect.{ContextShift, IO}
import io.circe.Json
import org.http4s._
import org.http4s.headers.{Authorization, `Content-Type`}
import org.http4s.multipart._
import org.http4s.circe._
import cats.implicits._
import org.http4s.circe.CirceEntityDecoder._
import org.scalatest.{FlatSpec, Matchers}
import receipt.{FileEntity, GenericMetaData, ReceiptEntity, StoredFile}
import routing.Routing

import scala.concurrent.ExecutionContext.Implicits.global

class ReceiptEndpointsSpec extends FlatSpec with Matchers {
  private implicit val cs: ContextShift[IO] = IO.contextShift(global)

  val accessToken = "Bearer token"
  val authHeader = Authorization(Credentials.Token(AuthScheme.Bearer, accessToken))

  it should "create receipt from file upload" in {
    val routing = new Routing(testAlgebras, testConfig, global)

    val textContent: EntityBody[TestProgram] = EntityEncoder[TestProgram, String].toEntity("file content").body
    val formBody = Multipart[TestProgram](
      Vector(
        Part.fileData("receipt", "receipt.png", textContent, `Content-Type`(org.http4s.MediaType.application.`octet-stream`)),
        Part.formData("total", "12.38"),
        Part.formData("description", "some description"),
        Part.formData("transactionTime", "1480130712396"),
        Part.formData("tags", "veggies,food")
      )
    )

    val request: Request[TestProgram] = Request(
      method = Method.POST,
      uri = Uri.uri("/receipt"),
      body = EntityEncoder[TestProgram, Multipart[TestProgram]].toEntity(formBody).body,
      headers = formBody.headers.put(authHeader)
    )
    val (sideEffects, response) = routing.routes.run(request).value.run.unsafeRunSync

    val receipt = response.map(_.as[ReceiptEntity].run.unsafeRunSync._2)

    receipt shouldBe Some(ReceiptEntity(
      id = defaultRandomId,
      userId = defaultUserId,
      total = Some(BigDecimal(12.38)),
      description = "some description",
      timestamp = 0,
      lastModified = 0,
      transactionTime = 1480130712396l,
      tags = List("veggies", "food"),
      files = List()
    ))

    sideEffects should contain(TestInterpreters.PendingFileSaved)
    sideEffects should contain(TestInterpreters.PendingFileSubmitted)
  }

  it should "reject receipt if file already exists" in {
    val routing = new Routing(testAlgebras.copy(
      fileStoreAlg = new FileStoreIntTest(md5Response = List(StoredFile(defaultUserId, "fileId", "md5")))), testConfig, global)

    val textContent: EntityBody[TestProgram] = EntityEncoder[TestProgram, String].toEntity("file content").body
    val formBody = Multipart[TestProgram](
      Vector(
        Part.fileData("receipt", "receipt.png", textContent, `Content-Type`(org.http4s.MediaType.application.`octet-stream`)),
        Part.formData("total", "12.38"),
        Part.formData("description", "some description"),
        Part.formData("transactionTime", "1480130712396"),
        Part.formData("tags", "veggies,food")
      )
    )

    val request: Request[TestProgram] = Request(
      method = Method.POST,
      uri = Uri.uri("/receipt"),
      body = EntityEncoder[TestProgram, Multipart[TestProgram]].toEntity(formBody).body,
      headers = formBody.headers.put(authHeader)
    )
    val (_, response) = routing.routes.run(request).value.run.unsafeRunSync

    val status = response.map(_.status)

    status shouldBe Some(Status.BadRequest)
  }

  it should "reject receipt from file upload if form field is not present" in {
    val routing = new Routing(testAlgebras, testConfig, global)

    val textContent: EntityBody[TestProgram] = EntityEncoder[TestProgram, String].toEntity("file content").body
    val formBody = Multipart[TestProgram](
      Vector(
        Part.fileData("receipt", "receipt.png", textContent, `Content-Type`(org.http4s.MediaType.application.`octet-stream`)),
        Part.formData("total", "12.38"),
        Part.formData("description", "some description"),
        Part.formData("tags", "veggies,food")
      )
    )

    val request: Request[TestProgram] = Request(
      method = Method.POST,
      uri = Uri.uri("/receipt"),
      body = EntityEncoder[TestProgram, Multipart[TestProgram]].toEntity(formBody).body,
      headers = formBody.headers.put(authHeader)
    )
    val (_, response) = routing.routes.run(request).value.run.unsafeRunSync

    val status = response.map(_.status)

    status shouldBe Some(Status.BadRequest)
  }

  it should "return a file content" in {
    val fileEntity =
      FileEntity(id = "1", parentId = None, ext = "txt", metaData = GenericMetaData(fileType = "TXT", length = 11), timestamp = 0l)
    val receipt = ReceiptEntity(id = "2", userId = defaultUserId, files = List(fileEntity))

    val routing = new Routing(testAlgebras.copy(
      receiptStoreAlg = new ReceiptStoreIntTest(List(receipt)),
      fileStoreAlg = new FileStoreIntTest(md5Response = List(StoredFile(defaultUserId, "fileId", "md5")))), testConfig, global)

    val request: Request[TestProgram] = Request(
      method = Method.GET,
      uri = Uri.unsafeFromString(s"/receipt/2/file/${fileEntity.id}.txt"),
      headers = Headers.of(authHeader)
    )

    val (_, response) = routing.routes.run(request).value.run.unsafeRunSync
    val content = response.map(res => StreamToString.streamToString(res.body))
    val contentType = response.flatMap(_.headers.get(`Content-Type`).map(_.value))

    content shouldBe Some("some text")
    contentType shouldBe Some("text/plain")
  }

  it should "read receipt by id" in {
    val receipt = ReceiptEntity(id = "2", userId = defaultUserId, files = List())

    val routing = new Routing(testAlgebras.copy(
      receiptStoreAlg = new ReceiptStoreIntTest(List(receipt)),
      fileStoreAlg = new FileStoreIntTest(md5Response = List(StoredFile(defaultUserId, "fileId", "md5")))), testConfig, global)

    val request: Request[TestProgram] = Request(
      method = Method.GET,
      uri = Uri.unsafeFromString(s"/receipt/${receipt.id}"),
      headers = Headers.of(authHeader)
    )

    val (_, response) = routing.routes.run(request).value.run.unsafeRunSync
    val responseReceipt = response.map(_.as[ReceiptEntity].run.unsafeRunSync._2)

    responseReceipt shouldBe Some(receipt)
  }

  it should "patch a receipt" in {
    val receipt = ReceiptEntity(id = "2", userId = defaultUserId, files = List())

    val routing = new Routing(testAlgebras.copy(
      receiptStoreAlg = new ReceiptStoreIntTest(List(receipt)),
      fileStoreAlg = new FileStoreIntTest(md5Response = List(StoredFile(defaultUserId, "fileId", "md5")))), testConfig, global)

    val patchJson = Json.arr(
      Json.obj(
        "op" -> Json.fromString("replace"),
        "path" -> Json.fromString("/description"),
        "value" -> Json.fromString("some new description")
      ),
      Json.obj(
        "op" -> Json.fromString("replace"),
        "path" -> Json.fromString("/total"),
        "value" -> Json.fromString("12.38")
      )
    )

    val request: Request[TestProgram] = Request(
      method = Method.PATCH,
      uri = Uri.unsafeFromString(s"/receipt/${receipt.id}"),
      body = EntityEncoder[TestProgram, Json].toEntity(patchJson).body,
      headers = Headers.of(authHeader)
    )

    val (_, response) = routing.routes.run(request).value.run.unsafeRunSync
    val responseReceipt = response.map(_.as[ReceiptEntity].run.unsafeRunSync._2)

    responseReceipt.map(_.description) shouldBe Some("some new description")
    responseReceipt.flatMap(_.total) shouldBe Some(BigDecimal("12.38"))
  }

  it should "unset total after patch with null" in {
    val receipt = ReceiptEntity(id = "2", userId = defaultUserId, files = List())

    val routing = new Routing(testAlgebras.copy(
      receiptStoreAlg = new ReceiptStoreIntTest(List(receipt)),
      fileStoreAlg = new FileStoreIntTest(md5Response = List(StoredFile(defaultUserId, "fileId", "md5")))), testConfig, global)

    val patchJson = Json.arr(
      Json.obj(
        "op" -> Json.fromString("replace"),
        "path" -> Json.fromString("/total"),
        "value" -> Json.Null
      )
    )

    val request: Request[TestProgram] = Request(
      method = Method.PATCH,
      uri = Uri.unsafeFromString(s"/receipt/${receipt.id}"),
      body = EntityEncoder[TestProgram, Json].toEntity(patchJson).body,
      headers = Headers.of(authHeader)
    )

    val (_, response) = routing.routes.run(request).value.run.unsafeRunSync
    val responseReceipt = response.map(_.as[ReceiptEntity].run.unsafeRunSync._2)

    responseReceipt.flatMap(_.total) shouldBe None
  }

  it should "unset total after patch with remove" in {
    val receipt = ReceiptEntity(id = "2", userId = defaultUserId, files = List())

    val routing = new Routing(testAlgebras.copy(
      receiptStoreAlg = new ReceiptStoreIntTest(List(receipt)),
      fileStoreAlg = new FileStoreIntTest(md5Response = List(StoredFile(defaultUserId, "fileId", "md5")))), testConfig, global)

    val patchJson = Json.arr(
      Json.obj(
        "op" -> Json.fromString("remove"),
        "path" -> Json.fromString("/total")
      )
    )

    val request: Request[TestProgram] = Request(
      method = Method.PATCH,
      uri = Uri.unsafeFromString(s"/receipt/${receipt.id}"),
      body = EntityEncoder[TestProgram, Json].toEntity(patchJson).body,
      headers = Headers.of(authHeader)
    )

    val (_, response) = routing.routes.run(request).value.run.unsafeRunSync
    val responseReceipt = response.map(_.as[ReceiptEntity].run.unsafeRunSync._2)

    responseReceipt.flatMap(_.total) shouldBe None
  }

  it should "set tags with a patch" in {
    val receipt = ReceiptEntity(id = "2", userId = defaultUserId, files = List())

    val routing = new Routing(testAlgebras.copy(
      receiptStoreAlg = new ReceiptStoreIntTest(List(receipt)),
      fileStoreAlg = new FileStoreIntTest(md5Response = List(StoredFile(defaultUserId, "fileId", "md5")))), testConfig, global)

    val patchJson = Json.arr(
      Json.obj(
        "op" -> Json.fromString("replace"),
        "path" -> Json.fromString("/tags"),
        "value" -> Json.arr(Json.fromString("vegetables"), Json.fromString("food"))
      )
    )

    val request: Request[TestProgram] = Request(
      method = Method.PATCH,
      uri = Uri.unsafeFromString(s"/receipt/${receipt.id}"),
      body = EntityEncoder[TestProgram, Json].toEntity(patchJson).body,
      headers = Headers.of(authHeader)
    )

    val (_, response) = routing.routes.run(request).value.run.unsafeRunSync
    val responseReceipt = response.map(_.as[ReceiptEntity].run.unsafeRunSync._2)

    responseReceipt.map(_.tags) shouldBe Some(List("vegetables", "food"))
  }

  // TODO verify that file was actually deleted after switching to state
  it should "delete a receipt" in {
    val receipt = ReceiptEntity(id = "2", userId = defaultUserId, files = List())

    val routing = new Routing(testAlgebras.copy(
      receiptStoreAlg = new ReceiptStoreIntTest(List(receipt)),
      fileStoreAlg = new FileStoreIntTest(md5Response = List(StoredFile(defaultUserId, "fileId", "md5")))), testConfig, global)

    val request: Request[TestProgram] = Request(
      method = Method.DELETE,
      uri = Uri.unsafeFromString(s"/receipt/${receipt.id}"),
      headers = Headers.of(authHeader)
    )

    val (_, response) = routing.routes.run(request).value.run.unsafeRunSync
    val status = response.map(_.status)

    status shouldBe Some(Status.NoContent)
  }

  it should "return list of receipts" in {
    val receipt = ReceiptEntity(id = "2", userId = defaultUserId, files = List())

    val routing = new Routing(testAlgebras.copy(
      receiptStoreAlg = new ReceiptStoreIntTest(List(receipt)),
      fileStoreAlg = new FileStoreIntTest(md5Response = List(StoredFile(defaultUserId, "fileId", "md5")))), testConfig, global)

    val request: Request[TestProgram] = Request(
      method = Method.GET,
      uri = Uri.uri("/receipt"),
      headers = Headers.of(authHeader)
    )

    val (_, response) = routing.routes.run(request).value.run.unsafeRunSync
    val responseReceipts = response.map(_.as[List[ReceiptEntity]].run.unsafeRunSync._2)

    responseReceipts shouldBe Some(List(receipt))
  }

}