package service

import java.io.{File, FileInputStream, InputStream}
import java.util.concurrent.Executors
import java.security.{DigestInputStream, MessageDigest}
import akka.stream.{IOResult, Materializer}
import akka.stream.scaladsl.{Source, _}
import akka.util.ByteString
import cats.effect.IO
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.{PutObjectRequest, PutObjectResult}
import com.typesafe.scalalogging.Logger
import model.{FileEntity, ImageMetadata}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait FileService {

  def save(userId: String, file: File, ext: String): Future[Seq[FileEntity]]

  def fetch(userId: String, fileId: String): Source[ByteString, Future[IOResult]]

  def fetchInputStream(userId: String, fileId: String): InputStream

  def delete(userId: String, fileId: String): Future[Unit]

  protected def toFileEntity(
      parentFileId: Option[String],
      fileId: String,
      file: File,
      ext: String,
      md5: Option[String]
  ): FileEntity = {

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

class S3FileService(materializer: Materializer, fileCachingService: FileCachingService, imageResizingService: ImageResizingService)
    extends FileService
    with Retry {
  val s3Bucket = sys.env("S3_BUCKET")
  val s3Region = sys.env("S3_REGION")
  val logger   = Logger(LoggerFactory.getLogger("S3FileService"))

  implicit val mat: Materializer = materializer
  implicit val irs               = imageResizingService
  implicit val ec                = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  lazy val amazonS3Client = {
    val credentials = new BasicAWSCredentials(sys.env("S3_ACCESS_KEY"), sys.env("S3_SECRET_ACCESS_KEY"))

    val amazonS3ClientBuilder = AmazonS3ClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(credentials))
    val customS3Endpoint = sys.env("S3_CUSTOM_ENDPOINT")
    if (customS3Endpoint.length > 0) {
      amazonS3ClientBuilder.withEndpointConfiguration(new EndpointConfiguration(customS3Endpoint, s3Region)).build()
    } else {
      amazonS3ClientBuilder.withRegion(s3Region).build()
    }
  }

  override def save(userId: String, file: File, ext: String): Future[Seq[FileEntity]] = {
    val fileId = java.util.UUID.randomUUID.toString

    val retryIntervals = Seq(1.seconds, 10.seconds, 30.seconds)

    logger.info(s"Uploading file ${file.getAbsolutePath}")
    val uploadResult = retry(upload(userId, fileId, file), retryIntervals)
    val fileEntity   = toFileEntity(None, fileId, file, ext, Some(md5(file)))

    val futures: Seq[IO[FileEntity]] = if (fileEntity.metaData.isInstanceOf[ImageMetadata]) {
      val resizedFileId = java.util.UUID.randomUUID.toString
      val resizedFileEntity: IO[FileEntity] = imageResizingService
        .resize(file, WebSize)
        .flatMap(resizedFile => {
          logger.info(s"Starting to upload a resized file $resizedFileId")
          retry(upload(userId, resizedFileId, resizedFile).map(ur => resizedFile), retryIntervals)
        })
        .map(resizedFile => {
          val fileEntity = toFileEntity(Some(fileId), resizedFileId, resizedFile, ext, None)
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

    // FIXME - remove Future
    Future.sequence(futures.map(iof => iof.unsafeToFuture()))
  }

  val upload: (String, String, File) => IO[PutObjectResult] = (userId, fileId, file) => {
    val putObjectRequest = new PutObjectRequest(s3Bucket, s"user/$userId/$fileId", file)

    // FIXME - remove Future
    IO.fromFuture(IO(Future {
      fileCachingService.cacheFile(userId, fileId, file)
      amazonS3Client.putObject(putObjectRequest)
    }))
  }

  val fetchFromS3: (String, String) => Source[ByteString, Future[IOResult]] = (userId, fileId) => {
    val fileStream = () => amazonS3Client.getObject(s3Bucket, s"user/$userId/$fileId").getObjectContent
    StreamConverters.fromInputStream(fileStream)
  }

  override def fetch(userId: String, fileId: String): Source[ByteString, Future[IOResult]] = {
    fileCachingService
      .get(userId, fileId)
      .getOrElse(fetchFromS3(userId, fileId).via(fileCachingService.cacheFlow(userId, fileId)))
  }

  override def delete(userId: String, fileId: String): Future[Unit] = {
    Future.successful(amazonS3Client.deleteObject(s3Bucket, s"user/$userId/$fileId"))
  }
  override def fetchInputStream(userId: String, fileId: String): InputStream =
    amazonS3Client.getObject(s3Bucket, s"user/$userId/$fileId").getObjectContent
}

object FileService {

  def s3(materializer: Materializer, fileCachingService: FileCachingService, imageResizingService: ImageResizingService) =
    new S3FileService(materializer, fileCachingService, imageResizingService)

}
