package service

import akka.stream.Materializer
import akka.stream.scaladsl.{Source, _}
import akka.util.ByteString
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult
import com.mfglabs.commons.aws.s3.{AmazonS3AsyncClient, S3StreamBuilder}
import com.typesafe.config.Config
import model.{FileEntity, GenericMetadata}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

// https://github.com/minio/minio-java
class FileService(config: Config, materializer: Materializer) {

  implicit val mat: Materializer = materializer

  lazy val s3StreamBuilder = S3StreamBuilder(new AmazonS3AsyncClient(
    new BasicAWSCredentials(config.getString("s3.accessKey"), config.getString("s3.secretAccessKey"))))

  def save(userId: String, source: Source[ByteString, Any], ext: String): Future[FileEntity] = {


    // FIXME - just fo testing
    val file = FileEntity(ext = ext, metaData = GenericMetadata(fileType = "TXT", length = 11))

    val s3FileFlow: Flow[ByteString, CompleteMultipartUploadResult, Unit] = s3StreamBuilder
      .uploadStreamAsFile(config.getString("s3.bucket"), s"user/${userId}/${file.id}", chunkUploadConcurrency = 2)
    val resultSink: Sink[CompleteMultipartUploadResult, Future[String]] = Sink.fold[String, CompleteMultipartUploadResult]("")(_ + _.getLocation())

    val runnable: RunnableGraph[Future[String]] = source.via(s3FileFlow).toMat(resultSink)(Keep.right)

    runnable.run().map(_ => file)
  }

  def fetch(userId: String, fileId: String): Source[ByteString, Unit] = {
    s3StreamBuilder.getFileAsStream(config.getString("s3.bucket"), s"user/${userId}/${fileId}")
  }

}
