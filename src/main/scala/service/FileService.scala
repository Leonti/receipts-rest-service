package service

import java.io.{File, InputStream}

import akka.NotUsed
import akka.stream.{ClosedShape, IOResult, Materializer, SourceShape}
import akka.stream.scaladsl.{Flow, Source, _}
import akka.util.ByteString
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{CompleteMultipartUploadResult, PutObjectRequest, PutObjectResult}
import com.typesafe.config.Config
import model.{FileEntity, GenericMetadata, ImageMetadata}
import util.SimpleImageInfo

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

trait FileService {

  def save(userId: String, file: File, ext: String): Future[FileEntity]

  def fetch(userId: String, fileId: String): Source[ByteString, Future[IOResult]]

  def delete(userId: String, fileId: String): Future[Unit]

  protected def save(userId: String, fileId: String, file: File, ext: String)(implicit materializer: Materializer): Future[FileEntity] = {

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

    Future.successful(toFileEntity())
  }
}

class S3FileService(config: Config, materializer: Materializer) extends FileService {
  implicit val mat: Materializer = materializer

  val credentials = new BasicAWSCredentials(config.getString("s3.accessKey"), config.getString("s3.secretAccessKey"))
  val amazonS3Client = new AmazonS3Client(credentials)

  override def save(userId: String, file: File, ext: String): Future[FileEntity] = {
    val fileId = java.util.UUID.randomUUID.toString;

    val putObjectRequest = new PutObjectRequest(
      config.getString("s3.bucket"),
      s"user/${userId}/${fileId}", file);

    val uploadResult: PutObjectResult = amazonS3Client.putObject(putObjectRequest)

    println(s"File uploaded to S3 with $uploadResult");
    save(userId, fileId, file, ext)
  }

  override def fetch(userId: String, fileId: String): Source[ByteString, Future[IOResult]] = {
    val fileStream = () => amazonS3Client.getObject(config.getString("s3.bucket"), s"user/${userId}/${fileId}")
      .getObjectContent()

    StreamConverters.fromInputStream(fileStream)
  }

  override def delete(userId: String, fileId: String): Future[Unit] = {
    Future.successful(amazonS3Client.deleteObject(config.getString("s3.bucket"), s"user/${userId}/${fileId}"))
  }
}

object FileService {

  def s3(config: Config, materializer: Materializer) = new S3FileService(config, materializer)

}