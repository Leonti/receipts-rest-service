import model.ReceiptEntity
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, FlatSpec}
import org.scalatest._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar

import org.mockito.Matchers.any
import repository.ReceiptRepository
import service.ReceiptService
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Right

class ReceiptServiceSpec extends FlatSpec with Matchers with MockitoSugar with ScalaFutures {

  it should "create receipt" in {

    val receipt = ReceiptEntity(userId = "123")
    val receiptRepository = mock[ReceiptRepository]
    when(receiptRepository.save(any[ReceiptEntity])).thenReturn(Future(receipt))

    val receiptService = new ReceiptService(receiptRepository)

    whenReady(receiptService.createReceipt("user id", "file id")) { result =>
      result shouldBe receipt
    }
  }

  it should "return receipts for user" in {
    val receiptRepository = mock[ReceiptRepository]

    val receipts = List(ReceiptEntity(userId = "userId"))
    when(receiptRepository.findForUserId("userId")).thenReturn(Future(receipts))

    val receiptService = new ReceiptService(receiptRepository)

    whenReady(receiptService.findForUserId("userId")) { result =>
      result shouldBe receipts
    }
  }

  it should "return specific receipt" in {
    val receiptRepository = mock[ReceiptRepository]

    val receipt = ReceiptEntity(id = "1", userId = "userId")
    when(receiptRepository.findById(receipt.id)).thenReturn(Future(Some(receipt)))
    when(receiptRepository.save(receipt)).thenReturn(Future(receipt))

    val receiptService = new ReceiptService(receiptRepository)

    whenReady(receiptService.save(receipt)) { savedReceipt =>
      savedReceipt.id shouldBe "1"
    }
  }

  it should "add a file to existing receipt" in {
    val receiptRepository = mock[ReceiptRepository]

    val receipt = ReceiptEntity(id = "1", userId = "userId")
    when(receiptRepository.findById(receipt.id)).thenReturn(Future(Some(receipt)))
    when(receiptRepository.save(any[ReceiptEntity])).thenReturn(Future(receipt))

    val receiptService = new ReceiptService(receiptRepository)

    whenReady(receiptService.addFileToReceipt("1", "fileId")) { savedReceipt =>
      savedReceipt shouldBe Some(receipt)
    }
  }

  it should "return None if adding a file to non-existing receipt" in {
    val receiptRepository = mock[ReceiptRepository]

    val receipt = ReceiptEntity(id = "1", userId = "userId")
    when(receiptRepository.findById(receipt.id)).thenReturn(Future(None))
    when(receiptRepository.save(any[ReceiptEntity])).thenReturn(Future(receipt))

    val receiptService = new ReceiptService(receiptRepository)

    whenReady(receiptService.addFileToReceipt("1", "fileId")) { result =>
      result shouldBe None
    }
  }

}
