import model._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import cats.effect.IO
import org.http4s.client.blaze.{BlazeClientConfig, Http1Client}
import org.scalatest.time.{Millis, Seconds, Span}
import scala.concurrent.duration._

class ReceiptSpec extends FlatSpec with Matchers with ScalaFutures {

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(120, Seconds), interval = Span(1000, Millis))

  private val httpClient = Http1Client[IO](
    BlazeClientConfig.defaultConfig.copy(
      responseHeaderTimeout = 60.seconds,
      requestTimeout = 60.second
    )).unsafeRunSync
  val userTestUtils = new UserTestUtils(httpClient)
  val receiptTestUtils = new ReceiptTestUtils(httpClient)

  it should "create a receipt from an image" in {

    val receiptAction = for {
      accessToken      <- userTestUtils.createUser.map(_._2)
      firstReceiptEntity <- receiptTestUtils.createReceipt(receiptTestUtils.createImageFileContent, accessToken.value)
      receiptEntity <- receiptTestUtils.getProcessedReceipt(firstReceiptEntity.id, accessToken.value)
    } yield receiptEntity

    whenReady(receiptAction.unsafeToFuture()) { receiptEntity =>
      receiptEntity.files.length shouldBe 2

      receiptEntity.total shouldBe ReceiptTestUtils.total
      receiptEntity.description shouldBe ReceiptTestUtils.description
      receiptEntity.transactionTime shouldBe ReceiptTestUtils.transactionTime
      receiptEntity.tags shouldBe ReceiptTestUtils.tags

      receiptEntity.files.head.metaData match {
        case ImageMetaData(_, _, width, height) =>
          width shouldBe 50
          height shouldBe 67
        case _ => fail("Metadata should be of an IMAGE type!")
      }
      receiptEntity.files(1).metaData match {
        case ImageMetaData(_, _, width, height) =>
          width shouldBe 50
          height shouldBe 67
        case _ => fail("Metadata should be of an IMAGE type!")
      }
    }
  }

  it should "reject receipt with the same file" in {

    val rejectedReceiptAction = for {
      accessToken      <- userTestUtils.createUser.map(_._2)
      firstReceiptEntity <- receiptTestUtils.createReceipt(receiptTestUtils.createImageFileContent, accessToken.value)
      _ <- receiptTestUtils.getProcessedReceipt(firstReceiptEntity.id, accessToken.value)
      secondReceipt <- receiptTestUtils.createReceiptEither(receiptTestUtils.createImageFileContent, accessToken.value)
    } yield secondReceipt

    whenReady(rejectedReceiptAction.unsafeToFuture()) { errorResponse =>
      errorResponse shouldBe Left("Failed to create, status code 400")
    }
  }

  it should "list receipts for a user" in {

    val receiptListAction = for {
      accessToken      <- userTestUtils.createUser.map(_._2)
      firstReceiptEntity <- receiptTestUtils.createReceipt(receiptTestUtils.createImageFileContent, accessToken.value)
      _ <- receiptTestUtils.getProcessedReceipt(firstReceiptEntity.id, accessToken.value)
      receiptList <- receiptTestUtils.fetchReceiptList(accessToken.value)
    } yield receiptList

    whenReady(receiptListAction.unsafeToFuture()) { receipts =>
      receipts.length shouldBe 1
    }
  }

  it should "patch a receipt" in {

    val patchedReceiptAction = for {
      accessToken      <- userTestUtils.createUser.map(_._2)
      firstReceiptEntity <- receiptTestUtils.createReceipt(receiptTestUtils.createImageFileContent, accessToken.value)
      receipt <- receiptTestUtils.getProcessedReceipt(firstReceiptEntity.id, accessToken.value)
      _ <- {
        val patch = """[
                      |  {
                      |    "op": "replace",
                      |    "path": "/description",
                      |    "value": "some new description"
                      |  }
                      |]""".stripMargin
        receiptTestUtils.patchReceipt(receipt.id, patch, accessToken.value)
      }
      updatedReceipt <- receiptTestUtils.fetchReceipt(receipt.id, accessToken.value)
    } yield updatedReceipt

    whenReady(patchedReceiptAction.unsafeToFuture()) { receiptEntity =>
      receiptEntity.description shouldBe "some new description"
    }
  }

  it should "serve a file for a receipt" in {

    val receiptFileAction = for {
      accessToken      <- userTestUtils.createUser.map(_._2)
      firstReceiptEntity <- receiptTestUtils.createReceipt(receiptTestUtils.createTextFileContent("receipt content"), accessToken.value)
      receipt <- receiptTestUtils.getProcessedReceipt(firstReceiptEntity.id, accessToken.value)
      fileResponse <- receiptTestUtils.fetchReceiptFile(receipt.id, receipt.files.head.id, accessToken.value)
    } yield fileResponse

    whenReady(receiptFileAction.unsafeToFuture()) { bytes =>
      bytes.map(_.toChar).mkString should include("receipt content")
    }
  }

  it should "delete a receipt" in {

    val receiptDeleteAction = for {
      accessToken      <- userTestUtils.createUser.map(_._2)
      firstReceiptEntity <- receiptTestUtils.createReceipt(receiptTestUtils.createImageFileContent, accessToken.value)
      receipt <- receiptTestUtils.getProcessedReceipt(firstReceiptEntity.id, accessToken.value)
      receiptListBefore <- receiptTestUtils.fetchReceiptList(accessToken.value)
      _ <- receiptTestUtils.deleteReceipt(receipt.id, accessToken.value)
      receiptListAfter <- receiptTestUtils.fetchReceiptList(accessToken.value)
    } yield (receiptListBefore, receiptListAfter)

    whenReady(receiptDeleteAction.unsafeToFuture()) {
      case (receiptsBeforeDelete, receipts) =>
        receiptsBeforeDelete.length shouldBe 1
        receipts.length shouldBe 0
    }
  }

}
