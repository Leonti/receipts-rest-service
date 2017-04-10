import model._
import ocr.model.OcrTextAnnotation
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.mockito.Matchers.any
import repository.{OcrRepository, ReceiptRepository}
import service.ReceiptService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Right

class ReceiptServiceSpec extends FlatSpec with Matchers with MockitoSugar with ScalaFutures {

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  it should "create receipt" in {

    val receipt           = ReceiptEntity(userId = "123")
    val receiptRepository = mock[ReceiptRepository]
    val ocrRepository     = mock[OcrRepository]

    when(receiptRepository.save(any[ReceiptEntity])).thenReturn(Future(receipt))
    val receiptService = new ReceiptService(receiptRepository, ocrRepository)

    whenReady(
      receiptService.createReceipt(
        userId = "user id",
        total = Some(BigDecimal("12.38")),
        description = "some description",
        transactionTime = 1480130712396l,
        tags = List("veggies", "food")
      )) { result =>
      result shouldBe receipt
    }
  }

  it should "return receipts for user" in {
    val receiptRepository = mock[ReceiptRepository]
    val ocrRepository     = mock[OcrRepository]

    val receipts = List(ReceiptEntity(userId = "userId"))
    when(receiptRepository.findForUserId("userId")).thenReturn(Future(receipts))

    val receiptService = new ReceiptService(receiptRepository, ocrRepository)

    whenReady(receiptService.findForUserId("userId")) { result =>
      result shouldBe receipts
    }
  }

  it should "return specific receipt" in {
    val receiptRepository = mock[ReceiptRepository]
    val ocrRepository     = mock[OcrRepository]

    val receipt = ReceiptEntity(id = "1", userId = "userId")
    when(receiptRepository.findById(receipt.id)).thenReturn(Future(Some(receipt)))
    when(receiptRepository.save(any[ReceiptEntity])).thenReturn(Future(receipt))

    val receiptService = new ReceiptService(receiptRepository, ocrRepository)

    whenReady(receiptService.save(receipt)) { savedReceipt =>
      savedReceipt.id shouldBe "1"
    }
  }

  it should "add a file to existing receipt" in {
    val receiptRepository = mock[ReceiptRepository]
    val ocrRepository     = mock[OcrRepository]

    val receipt = ReceiptEntity(id = "1", userId = "userId")
    when(receiptRepository.addFileToReceipt(any[String], any[FileEntity])).thenReturn(Future.successful(()))
    when(receiptRepository.findById(receipt.id)).thenReturn(Future(Some(receipt)))

    val receiptService = new ReceiptService(receiptRepository, ocrRepository)

    whenReady(
      receiptService.addFileToReceipt(
        "1",
        FileEntity(id = "1", parentId = None, ext = "png", metaData = ImageMetadata(length = 11, width = 1, height = 1)))) {
      savedReceipt =>
        savedReceipt shouldBe Some(receipt)
    }
  }

  it should "return None if adding a file to non-existing receipt" in {
    val receiptRepository = mock[ReceiptRepository]
    val ocrRepository     = mock[OcrRepository]

    val receipt = ReceiptEntity(id = "1", userId = "userId")
    when(receiptRepository.addFileToReceipt(any[String], any[FileEntity])).thenReturn(Future.successful(()))
    when(receiptRepository.findById(receipt.id)).thenReturn(Future(None))

    val receiptService = new ReceiptService(receiptRepository, ocrRepository)

    whenReady(
      receiptService.addFileToReceipt(
        "1",
        FileEntity(id = "1", parentId = None, ext = "png", metaData = ImageMetadata(length = 11, width = 1, height = 1)))) { result =>
      result shouldBe None
    }
  }

}
