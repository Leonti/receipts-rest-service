import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.stream.scaladsl._
import akka.http.scaladsl.model.ContentTypes._
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult
import com.mfglabs.commons.aws.s3.{AmazonS3AsyncClient, S3StreamBuilder}
import com.typesafe.config.Config

import scala.concurrent.Future

class FileService(config: Config, materializer: Materializer) {

  implicit val mat: Materializer = materializer

  lazy val s3StreamBuilder = S3StreamBuilder(new AmazonS3AsyncClient(
    new BasicAWSCredentials(config.getString("s3.accessKey"), config.getString("s3.secretAccessKey"))))

  def save(source: Source[ByteString, Any]): Future[String] = {
    val fileId = java.util.UUID.randomUUID.toString

    val s3FileFlow: Flow[ByteString, CompleteMultipartUploadResult, Unit] = s3StreamBuilder
      .uploadStreamAsFile(config.getString("s3.bucket"), s"user/${fileId}", chunkUploadConcurrency = 2)
    val resultSink: Sink[CompleteMultipartUploadResult, Future[String]] = Sink.fold[String, CompleteMultipartUploadResult]("")(_ + _.getLocation())

    val runnable: RunnableGraph[Future[String]] = source.via(s3FileFlow).toMat(resultSink)(Keep.right)

    runnable.run()
  }

}
