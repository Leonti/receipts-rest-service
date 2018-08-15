package service
import java.io._
import java.nio.charset.StandardCharsets
import java.util.zip.{ZipEntry, ZipOutputStream}

import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import akka.util.ByteString
import model.{FileEntity, ReceiptEntity}

import scala.concurrent.{ExecutionContextExecutor, Future}
import io.circe.syntax._

case class ReceiptsBackup(source: Source[ByteString, Future[IOResult]], filename: String)

class BackupService(receiptPrograms: ReceiptPrograms[Future], fileService: FileService)(implicit system: ActorSystem,
                                                                                        executor: ExecutionContextExecutor,
                                                                                        materializer: ActorMaterializer) {

  case class FileToZip(path: String, source: Source[ByteString, Any])

  private val fetchFilesToZip: String => Future[Seq[FileToZip]] = userId => {
    val receiptWithMainFiles: ReceiptEntity => ReceiptEntity =
      receipt => receipt.copy(files = receipt.files.filter(_.parentId.isEmpty))

    val fileToZip: FileEntity => FileToZip =
      fileEntity =>
        FileToZip(
          path = fileEntity.id + "." + fileEntity.ext,
          source = fileService.fetch(userId, fileEntity.id)
      )

    val receiptJsonEntry: Seq[ReceiptEntity] => FileToZip = receipts => {
      val receiptsAsJson = receipts.asJson.spaces2
      val byteString     = ByteString(receiptsAsJson.getBytes(StandardCharsets.UTF_8))
      FileToZip(
        path = "receipts.json",
        source = Source[ByteString](List(byteString))
      )
    }

    val userReceipts: Future[Seq[ReceiptEntity]] = receiptPrograms.findForUser(userId)

    userReceipts
      .map(_.map(receiptWithMainFiles))
      .map { receipts =>
        val files: Seq[FileToZip] = receipts.flatMap(_.files).map(fileToZip)
        val jsonFile: FileToZip   = receiptJsonEntry(receipts)
        files.++(List(jsonFile))
      }
  }

  val createUserBackup: String => ReceiptsBackup = userId => {

    val inputStream  = new PipedInputStream()
    val outputStream = new PipedOutputStream(inputStream)

    fetchFilesToZip(userId).map { files =>
      val zipOutputStream                      = new ZipOutputStream(outputStream)
      val sink: Sink[ByteString, Future[Done]] = Sink.foreach[ByteString](bs => zipOutputStream.write(bs.toArray))

      val serialized = {
        var acc = Future { () }
        for (file <- files) {
          println(s"Processing ${file.path}")
          acc = acc.flatMap(unit => {

            val zipEntry = new ZipEntry(file.path)
            zipOutputStream.putNextEntry(zipEntry)

            file.source
              .runWith(sink)
              .map(done => {
                zipOutputStream.closeEntry()
              })
          })
        }
        acc
      }

      serialized.onComplete { result =>
        zipOutputStream.close()
      }
    }

    ReceiptsBackup(source = StreamConverters.fromInputStream(() => inputStream), filename = "backup.zip")
  }

}
