import TestInterpreters._
import authentication.BearerAuth
import cats.Id
import com.twitter.finagle.http.{FileElement, RequestBuilder}
import com.twitter.io.Buf.ByteArray.Owned
import io.finch.{Endpoint, Input}
import model.{ReceiptEntity, SubClaim, User}
import org.scalatest.{FlatSpec, Matchers}
import instances.identity._
import routing.ReceiptEndpoints
import service.{FileUploadPrograms, ReceiptPrograms}

class ReceiptEndpointsSpec extends FlatSpec with Matchers {

  val receiptInt = new ReceiptInterpreterId(List(), List())
  val fileInt = new FileInterpreterId()
  val randomInt = new RandomInterpreterId("", 0)
  val ocrInt = new OcrInterpreterId()

  private val USER_ID = "123-user"
  val successfulAuth: Endpoint[User] = new BearerAuth[Id, User](
    new TestVerificationAlg(Right(SubClaim(""))),
    _ => Some(User(USER_ID, "email", List()))
  ).auth

  it should "create receipt from file upload" in {
    val receiptRouting = new ReceiptEndpoints[Id](
      successfulAuth,
      new ReceiptPrograms(new ReceiptInterpreterId(List(), List()), fileInt, randomInt, ocrInt),
      new FileUploadPrograms("", fileInt, randomInt)
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
      files = List()
    ))
  }

  it should "read receipt by id" in {
    val receipt = ReceiptEntity(id = "2", userId = USER_ID, files = List(), description = "some description")
    val receiptRouting = new ReceiptEndpoints[Id](
      successfulAuth,
      new ReceiptPrograms(new ReceiptInterpreterId(List(receipt), List()), fileInt, randomInt, ocrInt),
      new FileUploadPrograms("", fileInt, randomInt)
    )

    val input = Input.get("/receipt/2").withHeaders("Authorization" -> "Bearer token")
    receiptRouting.getReceipt(input).awaitValueUnsafe() shouldBe Some(receipt)
  }

}
