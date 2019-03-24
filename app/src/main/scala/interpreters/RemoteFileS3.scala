package interpreters
import java.io.{File, InputStream}

import algebras.RemoteFileAlg
import cats.effect.{ContextShift, IO}
import fs2.Stream
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.PutObjectRequest
import receipt.RemoteFileId

import scala.concurrent.ExecutionContext

case class S3Config(region: String, bucket: String, accessKey: String, secretKey: String)

class RemoteFileS3(config: S3Config, amazonS3Client: AmazonS3, bec: ExecutionContext) extends RemoteFileAlg[IO] {
  private implicit val cs: ContextShift[IO] = IO.contextShift(bec)

  private def toS3Key(fileId: RemoteFileId): String = s"user/${fileId.userId.value}/files/${fileId.fileId}"

  override def saveRemoteFile(file: File, fileId: RemoteFileId): IO[Unit] =
    IO {
      amazonS3Client.putObject(new PutObjectRequest(config.bucket, toS3Key(fileId), file))
    }.map(_ => ())

  override def remoteFileStream(fileId: RemoteFileId): IO[Stream[IO, Byte]] = {
    IO(
      fs2.io.readInputStream(IO(amazonS3Client.getObject(config.bucket, toS3Key(fileId)).getObjectContent.asInstanceOf[InputStream]),
                             1024,
                             bec))
  }
  override def deleteRemoteFile(fileId: RemoteFileId): IO[Unit] = IO {
    amazonS3Client.deleteObject(config.bucket, toS3Key(fileId))
  }
}
