package interpreters
import java.io.{File, InputStream}

import algebras.RemoteFileAlg
import cats.effect.IO
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.PutObjectRequest
import model.RemoteFileId

case class S3Config(region: String, bucket: String, accessKey: String, secretKey: String)

class RemoteFileS3(config: S3Config) extends RemoteFileAlg[IO] {

  private lazy val amazonS3Client = {
    val credentials = new BasicAWSCredentials(config.accessKey, config.secretKey)

    val amazonS3ClientBuilder = AmazonS3ClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(credentials))
    amazonS3ClientBuilder.withRegion(config.region).build()
  }

  override def saveRemoteFile(file: File, fileId: RemoteFileId): IO[Unit] =
    IO {
      amazonS3Client.putObject(new PutObjectRequest(config.bucket, s"user/${fileId.userId}/${fileId.fileId}", file))
    }.map(_ => ())

  override def fetchRemoteFileInputStream(fileId: RemoteFileId): IO[InputStream] = IO {
    amazonS3Client.getObject(config.bucket, s"user/${fileId.userId}/${fileId.fileId}").getObjectContent
  }

  override def deleteRemoteFile(fileId: RemoteFileId): IO[Unit] = IO {
    amazonS3Client.deleteObject(config.bucket, s"user/${fileId.userId}/${fileId.fileId}")
  }
}
