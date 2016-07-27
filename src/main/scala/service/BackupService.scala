package service
import java.io._
import java.util.zip.{ZipEntry, ZipOutputStream}

import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import akka.util.ByteString
import model.{FileEntity, ReceiptEntity}

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContextExecutor, Future}

case class ReceiptsBackup(source: Source[ByteString, Future[IOResult]], filename: String)

class BackupService(receiptService: ReceiptService, fileService: FileService)
                   (implicit system: ActorSystem, executor: ExecutionContextExecutor, materializer: ActorMaterializer) {

  case class SourceToZip(entity: FileEntity, source: Source[ByteString, Future[IOResult]])
  case class ReceiptsToZip(receipts: List[ReceiptEntity], sources: List[SourceToZip])

  def userReceipts(userId: String) = {

    def isOriginal(fileEntity: FileEntity) = fileEntity.parentId.isEmpty

    def sourcesToZips(fileEntities: List[FileEntity]) = fileEntities.map(fileEntity =>
      SourceToZip(fileEntity, fileService.fetch(userId, fileEntity.id)))

    val inputStream = new PipedInputStream()
    val outputStream = new PipedOutputStream(inputStream)

    val receiptsToZip = for {
      receipts <- receiptService.findForUserId(userId)
      fileEntities <- Future.successful(receipts.flatMap(_.files.filter(isOriginal)))
    } yield ReceiptsToZip(receipts = receipts, sources = sourcesToZips(fileEntities)) // .take(3)

    val zipFileFuture: Future[Unit] = receiptsToZip.flatMap { receipts =>
    //  val fileOutputStream = new FileOutputStream("/home/leonti/Downloads/ziptest.zip")
      val zipOutputStream = new ZipOutputStream(outputStream)

      val fSerialized = {
        var fAccum = Future{()}
        for(source <- receipts.sources) {
          println(s"Processing ${source.entity.id}")
          fAccum = fAccum.flatMap(unit => {
            val sink: Sink[ByteString, Future[Done]] = Sink.foreach[ByteString](bs => zipOutputStream.write(bs.toArray))

            val zipEntry = new ZipEntry(source.entity.id + "." + source.entity.ext)
            zipOutputStream.putNextEntry(zipEntry)

            source.source.runWith(sink).map(done => {
              zipOutputStream.closeEntry()
            })
          })
        }
        fAccum
      }

      fSerialized.map(uni => {
        zipOutputStream.close()
      })
    }

    val source: Source[ByteString, Future[IOResult]] = StreamConverters.fromInputStream(() => inputStream)

    ReceiptsBackup(source = source, filename = "backup.zip")

  }

}
