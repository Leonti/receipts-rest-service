package interpreters

import java.io.File

import algebras.OcrAlg
import cats.effect.IO
import com.amazonaws.services.s3.AmazonS3
import org.http4s._
import org.http4s.headers._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._
import org.http4s.circe._
import io.circe.syntax._
import ocr._
import org.http4s.util.CaseInsensitiveString

object OcrIntepreter {
  case class OcrConfig(ocrHost: String, apiKey: String)
}

class OcrInterpreterTagless(httpClient: Client[IO],
                            config: S3Config,
                            amazonS3Client: AmazonS3,
                            ocrService: OcrService,
                            ocrConfig: OcrIntepreter.OcrConfig)
    extends OcrAlg[IO] {

  private implicit val ocrSearchResultDecoder: EntityDecoder[IO, OcrSearchResult] = jsonOf[IO, OcrSearchResult]

  override def ocrImage(file: File): IO[OcrTextAnnotation] = IO.fromFuture(IO(ocrService.ocrImage(file)))

  override def saveOcrResult(userId: String, receiptId: String, ocrResult: OcrTextAnnotation): IO[Unit] = IO {
    amazonS3Client.putObject(config.bucket, s"user/$userId/ocr/$receiptId", ocrResult.asJson.spaces2)
  }

  override def addOcrToIndex(userId: String, receiptId: String, ocrText: OcrText): IO[Unit] =
    httpClient
      .expect[String](
        POST(
          OcrContent(ocrText.text).asJson,
          Uri.unsafeFromString(s"${ocrConfig.ocrHost}/api/search/$userId/$receiptId"),
          Authorization(Credentials.Token(CaseInsensitiveString("ApiKey"), ocrConfig.apiKey))
        ))
      .map(_ => ())

  override def findIdsByText(userId: String, query: String): IO[Seq[String]] =
    httpClient
      .expect[OcrSearchResult](
        GET(
          Uri.unsafeFromString(s"${ocrConfig.ocrHost}/api/search/$userId").withQueryParam("q", query),
          Authorization(Credentials.Token(CaseInsensitiveString("ApiKey"), ocrConfig.apiKey))
        ))
      .map(_.ids)
}
