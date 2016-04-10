package service

import java.io.File

import akka.stream.{ClosedShape, Materializer, SourceShape}
import akka.stream.scaladsl.{Flow, Source, _}
import akka.util.ByteString
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult
import com.mfglabs.commons.aws.s3.{AmazonS3AsyncClient, S3StreamBuilder}
import com.typesafe.config.Config
import model.{FileEntity, GenericMetadata, ImageMetadata}
import util.SimpleImageInfo

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

trait FileService {

  def save(userId: String, file: File, ext: String): Future[FileEntity]

  def fetch(userId: String, fileId: String): Source[ByteString, Unit]


  protected def save(userId: String, fileId: String, file: File,
                     uploadFlow: Flow[ByteString, String, Unit], ext: String)(implicit materializer: Materializer): Future[FileEntity] = {

    def toFileEntity(): FileEntity = {
      val image: Option[SimpleImageInfo] = Try {
        Some(new SimpleImageInfo(file))
      } getOrElse(None)

      image match {
        case Some(i) =>
          FileEntity(
            id = fileId,
            ext = ext,
            metaData = ImageMetadata(length = file.length, width = i.getWidth(), height = i.getHeight()))
        case _ => FileEntity(id = fileId, ext = ext, metaData = GenericMetadata(length = file.length))
      }
    }

    val fileUploadSink: Sink[String, Future[String]] = Sink.last
    FileIO.fromFile(file).via(uploadFlow).runWith(fileUploadSink).map(_ => toFileEntity())
  }

}

class S3FileService(config: Config, materializer: Materializer) extends FileService {
  implicit val mat: Materializer = materializer

  lazy val s3StreamBuilder = S3StreamBuilder(new AmazonS3AsyncClient(
  new BasicAWSCredentials(config.getString("s3.accessKey"), config.getString("s3.secretAccessKey"))))


  def save(userId: String, file: File, ext: String): Future[FileEntity] = {
    val fileId = java.util.UUID.randomUUID.toString;

    val s3FileFlow: Flow[ByteString, String, Unit] = s3StreamBuilder
    .uploadStreamAsFile(config.getString("s3.bucket"), s"user/${userId}/${fileId}", chunkUploadConcurrency = 2)
    .map(uploadResult => {
      println("S3 UPLOAD FINISHED")
      uploadResult.getKey})

    save(userId, fileId, file, s3FileFlow, ext)
  }

  def fetch(userId: String, fileId: String): Source[ByteString, Unit] = {
    s3StreamBuilder.getFileAsStream(config.getString("s3.bucket"), s"user/${userId}/${fileId}")
  }
}

object FileService {

  def s3(config: Config, materializer: Materializer) = new S3FileService(config, materializer)

}
