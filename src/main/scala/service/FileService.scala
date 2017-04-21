package service

import java.io.File
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.stream.{IOResult, Materializer}
import akka.stream.scaladsl.{Source, _}
import akka.util.ByteString
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{PutObjectRequest, PutObjectResult}
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import model.{FileEntity, ImageMetadata}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait FileService {

  def save(userId: String, file: File, ext: String): Future[Seq[FileEntity]]

  def fetch(userId: String, fileId: String): Source[ByteString, Future[IOResult]]

  def delete(userId: String, fileId: String): Future[Unit]

  protected def toFileEntity(userId: String, parentFileId: Option[String], fileId: String, file: File, ext: String)(
      implicit materializer: Materializer,
      imageResizingService: ImageResizingService): FileEntity = {

    FileEntity(
      id = fileId,
      parentId = parentFileId,
      ext = ext,
      metaData = FileMetadataParser.parse(file)
    )
  }

}

class S3FileService(config: Config,
                    system: ActorSystem,
                    materializer: Materializer,
                    fileCachingService: FileCachingService,
                    imageResizingService: ImageResizingService)
    extends FileService
    with Retry {
  val logger = Logger(LoggerFactory.getLogger("S3FileService"))

  implicit val mat: Materializer = materializer
  implicit val irs               = imageResizingService
  implicit val ec                = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  val credentials    = new BasicAWSCredentials(config.getString("s3.accessKey"), config.getString("s3.secretAccessKey"))
  val amazonS3Client = new AmazonS3Client(credentials)

  override def save(userId: String, file: File, ext: String): Future[Seq[FileEntity]] = {
    val fileId = java.util.UUID.randomUUID.toString

    implicit val scheduler = system.scheduler
    val retryIntervals     = Seq(1.seconds, 10.seconds, 30.seconds)

    logger.info(s"Uploading file ${file.getAbsolutePath}")
    val uploadResult = retry(upload(userId, fileId, file), retryIntervals)
    val fileEntity   = toFileEntity(userId, None, fileId, file, ext)

    val futures: Seq[Future[FileEntity]] = if (fileEntity.metaData.isInstanceOf[ImageMetadata]) {
      val resizedFileId = java.util.UUID.randomUUID.toString
      val resizedFileEntity: Future[FileEntity] = imageResizingService
        .resize(file, WebSize)
        .flatMap(resizedFile => {
          logger.info(s"Starting to upload a resized file $resizedFileId")
          retry(upload(userId, resizedFileId, resizedFile).map(ur => resizedFile), retryIntervals)
        })
        .map(resizedFile => {
          val fileEntity = toFileEntity(userId, Some(fileId), resizedFileId, resizedFile, ext)
          resizedFile.delete()
          fileEntity
        })

      Seq(uploadResult.map(ur => {
        logger.info(s"File uploaded to S3 with $ur")
        fileEntity
      }), resizedFileEntity)
    } else {
      Seq(uploadResult.map(ur => {
        logger.info(s"File uploaded to S3 with $ur")
        fileEntity
      }))
    }

    Future.sequence(futures)
  }

  val upload: (String, String, File) => Future[PutObjectResult] = (userId, fileId, file) => {
    val putObjectRequest = new PutObjectRequest(config.getString("s3.bucket"), s"user/$userId/$fileId", file)

    Future {
      fileCachingService.cacheFile(userId, fileId, file)
      amazonS3Client.putObject(putObjectRequest)
    }
  }

  val fetchFromS3: (String, String) => Source[ByteString, Future[IOResult]] = (userId, fileId) => {
    val fileStream = () => amazonS3Client.getObject(config.getString("s3.bucket"), s"user/$userId/$fileId").getObjectContent

    StreamConverters.fromInputStream(fileStream)
  }

  override def fetch(userId: String, fileId: String): Source[ByteString, Future[IOResult]] = {
    fileCachingService
      .get(userId, fileId)
      .getOrElse(fetchFromS3(userId, fileId).via(fileCachingService.cacheFlow(userId, fileId)))
  }

  override def delete(userId: String, fileId: String): Future[Unit] = {
    Future.successful(amazonS3Client.deleteObject(config.getString("s3.bucket"), s"user/$userId/$fileId"))
  }
}

object FileService {

  def s3(config: Config,
         system: ActorSystem,
         materializer: Materializer,
         fileCachingService: FileCachingService,
         imageResizingService: ImageResizingService) =
    new S3FileService(config, system, materializer, fileCachingService, imageResizingService)

}
