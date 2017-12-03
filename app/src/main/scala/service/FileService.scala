package service

import java.io.File
import java.util.concurrent.Executors

import akka.actor.{ActorSystem, Scheduler}
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
import java.security.{DigestInputStream, MessageDigest}
import java.io.{File, FileInputStream}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait FileService {

  def save(userId: String, file: File, ext: String): Future[Seq[FileEntity]]

  def fetch(userId: String, fileId: String): Source[ByteString, Future[IOResult]]

  def delete(userId: String, fileId: String): Future[Unit]

  protected def toFileEntity(
      userId: String,
      parentFileId: Option[String],
      fileId: String,
      file: File,
      ext: String,
      md5: Option[String]
  )(implicit materializer: Materializer, imageResizingService: ImageResizingService): FileEntity = {

    FileEntity(
      id = fileId,
      parentId = parentFileId,
      ext = ext,
      md5 = md5,
      metaData = FileMetadataParser.parse(file)
    )
  }

  def md5(file: File): String = {
    val buffer = new Array[Byte](8192)
    val md5    = MessageDigest.getInstance("MD5")

    val dis = new DigestInputStream(new FileInputStream(file), md5)
    try { while (dis.read(buffer) != -1) {} } finally { dis.close() }

    md5.digest.map("%02x".format(_)).mkString
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

  lazy val amazonS3Client: AmazonS3Client = {
    val credentials    = new BasicAWSCredentials(config.getString("s3.accessKey"), config.getString("s3.secretAccessKey"))
    val s3Client = new AmazonS3Client(credentials)
    val customS3Endpoint = config.getString("s3.customEndpoint")
    if (customS3Endpoint.length > 0) {
      s3Client.setEndpoint(customS3Endpoint)
    }
    s3Client
  }

  override def save(userId: String, file: File, ext: String): Future[Seq[FileEntity]] = {
    val fileId = java.util.UUID.randomUUID.toString

    implicit val scheduler: Scheduler = system.scheduler
    val retryIntervals     = Seq(1.seconds, 10.seconds, 30.seconds)

    logger.info(s"Uploading file ${file.getAbsolutePath}")
    val uploadResult = retry(upload(userId, fileId, file), retryIntervals)
    val fileEntity   = toFileEntity(userId, None, fileId, file, ext, Some(md5(file)))

    val futures: Seq[Future[FileEntity]] = if (fileEntity.metaData.isInstanceOf[ImageMetadata]) {
      val resizedFileId = java.util.UUID.randomUUID.toString
      val resizedFileEntity: Future[FileEntity] = imageResizingService
        .resize(file, WebSize)
        .flatMap(resizedFile => {
          logger.info(s"Starting to upload a resized file $resizedFileId")
          retry(upload(userId, resizedFileId, resizedFile).map(ur => resizedFile), retryIntervals)
        })
        .map(resizedFile => {
          val fileEntity = toFileEntity(userId, Some(fileId), resizedFileId, resizedFile, ext, None)
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
