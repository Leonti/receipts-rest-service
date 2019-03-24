import org.scalatest.{FlatSpec, Matchers}
import TestInterpreters._
import processing.FileProcessor
import queue.{OcrJob, ReceiptFileJob}
import receipt._
import user.UserId
import cats.implicits._

class FileProcessorSpec extends FlatSpec with Matchers {

  it should "process image file" in {

    val receiptId = "receiptId"
    val imageMetaData = ImageMetaData(width = 1, height = 2, length = 3)
    val fileExt = "jpg"
    val pendingFileId = "pending-file"

    val expectedFileEntity1 = FileEntity(receiptId, None, fileExt, imageMetaData, defaultTime)
    val expectedFileEntity2 = FileEntity(defaultRandomId, Some(expectedFileEntity1.id), fileExt, imageMetaData, defaultTime)

    val fileProcessor = new FileProcessor(
      new ReceiptStoreIntTest(List(
        ReceiptEntity(
          id = receiptId,
          userId = defaultUserId,
          files = List(expectedFileEntity1, expectedFileEntity2)
        )
      )),
      testAlgebras.localFileAlg,
      testAlgebras.remoteFileAlg,
      new ImageIntTest(true, imageMetaData),
      testAlgebras.randomAlg
    )

    val (sideEffects, jobs) = fileProcessor.processJob(ReceiptFileJob(
      userId = defaultUserId,
      receiptId = receiptId,
      remoteFileId = RemoteFileId(UserId(defaultUserId), receiptId),
      fileExt = fileExt,
      pendingFileId = pendingFileId
    )).run.unsafeRunSync()

    jobs shouldBe List(
      OcrJob(
        userId = defaultUserId,
        receiptId = receiptId,
        fileId = receiptId,
        pendingFileId = pendingFileId
      ))

    sideEffects should contain(FileEntityAdded(expectedFileEntity1))
    sideEffects should contain(FileEntityAdded(expectedFileEntity2))
    sideEffects should contain(LocalFileRemoved(defaultResizedFile))
    sideEffects should contain(LocalFileRemoved(defaultTmpFile))
  }

  it should "process non-image file" in {
    val receiptId = "receiptId"
    val genericMetaData = GenericMetaData(length = 3)
    val fileExt = "txt"
    val pendingFileId = "pending-file"

    val expectedFileEntity = FileEntity(receiptId, None, fileExt, genericMetaData, defaultTime)

    val fileProcessor = new FileProcessor(
      new ReceiptStoreIntTest(List(
        ReceiptEntity(
          id = receiptId,
          userId = defaultUserId,
          files = List(expectedFileEntity)
        )
      )),
      new LocalFileIntTest(genericMetaData = genericMetaData),
      testAlgebras.remoteFileAlg,
      new ImageIntTest(isImage = false),
      testAlgebras.randomAlg
    )

    val (sideEffects, jobs) = fileProcessor.processJob(ReceiptFileJob(
      userId = defaultUserId,
      receiptId = receiptId,
      remoteFileId = RemoteFileId(UserId(defaultUserId), receiptId),
      fileExt = fileExt,
      pendingFileId = pendingFileId
    )).run.unsafeRunSync()

    jobs shouldBe List()

    sideEffects should contain(FileEntityAdded(expectedFileEntity))
    sideEffects should contain(LocalFileRemoved(defaultTmpFile))
  }

}
