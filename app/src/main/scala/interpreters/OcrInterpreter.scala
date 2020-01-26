package interpreters

import java.io.{File, InputStream}

import algebras.OcrAlg
import cats.effect.{Blocker, ContextShift, IO}
import com.amazonaws.services.s3.AmazonS3
import fs2.Stream
import io.circe.syntax._
import ocr._
import config.S3Config

import scala.concurrent.ExecutionContext

object OcrIntepreter {
  case class OcrConfig(ocrHost: String, apiKey: String)
}

class OcrInterpreterTagless(
    config: S3Config,
    amazonS3Client: AmazonS3,
    ocrService: OcrService
) extends OcrAlg[IO] {

  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  override def ocrImage(file: File): IO[OcrTextAnnotation] = IO.fromFuture(IO(ocrService.ocrImage(file)))

  override def saveOcrResult(userId: String, receiptId: String, ocrResult: OcrTextAnnotation): IO[Unit] = IO {
    amazonS3Client.putObject(config.bucket, s"user/$userId/ocr/$receiptId", ocrResult.asJson.spaces2)
  }

  def tempGetOcrResult(userId: String, receiptId: String): IO[Stream[IO, Byte]] = {
    IO(
      fs2.io.readInputStream(
        IO(amazonS3Client.getObject(config.bucket, s"user/$userId/ocr/$receiptId").getObjectContent.asInstanceOf[InputStream]),
        1024,
        Blocker.liftExecutionContext(ExecutionContext.global)
      )
    )
  }

}
