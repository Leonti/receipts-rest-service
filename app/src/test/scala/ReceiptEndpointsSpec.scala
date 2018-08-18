import TestInterpreters._
import authentication.BearerAuth
import cats.Id
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

  val successfulAuth: Endpoint[User] = new BearerAuth[Id, User](
    new TestVerificationAlg(Right(SubClaim(""))),
    _ => Some(User("id", "email", List()))
  ).auth

  it should "read receipt by id" in {
    val receipt = ReceiptEntity(id = "2", userId = "123-user", files = List(), description = "some description")
    val receiptRouting = new ReceiptEndpoints[Id](
      successfulAuth,
      new ReceiptPrograms(new ReceiptInterpreterId(List(receipt), List()), fileInt, randomInt, ocrInt),
      new FileUploadPrograms("", fileInt, randomInt)
    )

    val input = Input.get("/receipt/2").withHeaders("Authorization" -> "Bearer token")
    receiptRouting.getReceipt(input).awaitValueUnsafe() shouldBe Some(receipt)
  }

}
