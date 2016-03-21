package service

import akka.stream.{ClosedShape, Materializer, SourceShape}
import akka.stream.scaladsl.{Flow, Source, _}
import akka.util.ByteString
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult
import com.mfglabs.commons.aws.s3.{AmazonS3AsyncClient, S3StreamBuilder}
import com.sksamuel.scrimage.{Image, ImageParseException}
import com.typesafe.config.Config
import model.{FileEntity, GenericMetadata, ImageMetadata}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

// https://github.com/minio/minio-java
trait FileService {

  def save(userId: String, source: Source[ByteString, Any], ext: String): Future[FileEntity]

  def fetch(userId: String, fileId: String): Source[ByteString, Unit]

  protected def save(userId: String, fileId: String, source: Source[ByteString, Any],
                     uploadFlow: Flow[ByteString, String, Unit], ext: String)(implicit materializer: Materializer): Future[FileEntity] = {

    val fileEntityFlow: Flow[ByteString, FileEntity, Unit] = Flow[ByteString].map(byteString => {

      val image: Option[Image] = Try {
        Some(Image(byteString.toArray))
      } getOrElse(None)

      image match {
        case Some(i) => FileEntity(id = fileId, ext = ext, metaData = ImageMetadata(length = byteString.length, width = i.width, height = i.height))
        case _ => FileEntity(id = fileId, ext = ext, metaData = GenericMetadata(length = byteString.length))
      }
    })

    val resultSink: Sink[FileEntity, Future[FileEntity]] = Sink.last
    val graph: RunnableGraph[Future[FileEntity]] = RunnableGraph.fromGraph(GraphDSL.create(resultSink) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._

        val bcast = builder.add(Broadcast[ByteString](2))
        val zip = builder.add(ZipWith[String, FileEntity, FileEntity]((left, right) => right))

        source ~> bcast ~> uploadFlow ~> zip.in0
        bcast ~> fileEntityFlow ~> zip.in1
        zip.out ~> sink.in

        ClosedShape
    })

    graph.run()
  }

}

class S3FileService(config: Config, materializer: Materializer) extends FileService {
  implicit val mat: Materializer = materializer

  lazy val s3StreamBuilder = S3StreamBuilder(new AmazonS3AsyncClient(
  new BasicAWSCredentials(config.getString("s3.accessKey"), config.getString("s3.secretAccessKey"))))


  def save(userId: String, source: Source[ByteString, Any], ext: String): Future[FileEntity] = {
    val fileId = java.util.UUID.randomUUID.toString;

    val s3FileFlow: Flow[ByteString, String, Unit] = s3StreamBuilder
    .uploadStreamAsFile(config.getString("s3.bucket"), s"user/${userId}/${fileId}", chunkUploadConcurrency = 2)
    .map(uploadResult => uploadResult.getKey)

    save(userId, fileId, source, s3FileFlow, ext)
  }

  def fetch(userId: String, fileId: String): Source[ByteString, Unit] = {
    s3StreamBuilder.getFileAsStream(config.getString("s3.bucket"), s"user/${userId}/${fileId}")
  }
}

object FileService {

  def s3(config: Config, materializer: Materializer) = new S3FileService(config, materializer)

}
