package interpreters

import algebras.ReceiptSearchAlg
import cats.effect.IO
import org.http4s.client.Client
import org.http4s.circe._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._
import org.http4s.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.util.CaseInsensitiveString
import ocr._
import config.SearchConfig

class ReceiptSearch(
    httpClient: Client[IO],
    searchConfig: SearchConfig
) extends ReceiptSearchAlg[IO] {

  case class SearchEntry(
      id: String,
      text: String
  )

  case class ArticleAdded(msg: String)

  def addOcrToIndex(userId: String, receiptId: String, ocrText: OcrText): IO[Unit] =
    httpClient
      .expect[ArticleAdded](
        POST(
          SearchEntry(id = receiptId, text = ocrText.text).asJson,
          Uri
            .unsafeFromString(s"${searchConfig.baseUrl}/add")
            .withQueryParam("userId", userId),
          Header.Raw(CaseInsensitiveString("Authorization"), searchConfig.apiKey)
        )
      )
      .map(_ => ())

  def findIdsByText(userId: String, query: String): IO[List[String]] =
    httpClient
      .expect[List[String]](
        GET(
          Uri
            .unsafeFromString(s"${searchConfig.baseUrl}/search")
            .withQueryParam("userId", userId)
            .withQueryParam("q", query)
            .withQueryParam("count", 200),
          Header.Raw(CaseInsensitiveString("Authorization"), searchConfig.apiKey)
        )
      )

}
