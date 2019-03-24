import TestConfig._
import cats.effect.{ContextShift, IO, Timer}
import cats.syntax.all._
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.multipart.Part
import pending.PendingFile
import receipt.ReceiptEntity

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object ReceiptTestUtils {
  val total           = Some(BigDecimal(12.38))
  val description     = "some description"
  val transactionTime = 1480130712396l
  val tags            = List("veggies", "food")
  val tagsAsString    = tags.reduce((acc, tag) => s"$acc,$tag")
}

class ReceiptTestUtils(httpClient: Client[IO]) {

  def getProcessedReceipt(receiptId: String, accessToken: String): IO[ReceiptEntity] = {
    pendingFilesToReceipt(receiptId, pendingFiles(accessToken), accessToken, 0)
  }

  private def pendingFiles(accessToken: String): IO[List[PendingFile]] = {
    import org.http4s.circe.CirceEntityCodec._

    httpClient.expect[List[PendingFile]](
      GET(
        org.http4s.Uri.unsafeFromString(s"$appHostPort/pending-file"),
        org.http4s.headers.Authorization(Credentials.Token(AuthScheme.Bearer, accessToken))
      )
    )
  }

  private def pendingFilesToReceipt(receiptId: String, pendingFilesIO: IO[List[PendingFile]], accessToken: String, retry: Int): IO[ReceiptEntity] = {
    implicit val timer: Timer[IO] = IO.timer(global)

    val checkInterval = 1.second

    if (retry > 60) {
      IO.raiseError(new RuntimeException(s"Could not get receipt entity in time $receiptId"))
    } else {
      for {
        pending <- pendingFilesIO
        receipt <- if (pending.exists(_.receiptId == receiptId))
          IO.sleep(checkInterval) *> pendingFilesToReceipt(receiptId, pendingFilesIO, accessToken, retry + 1)
        else
          fetchReceipt(receiptId, accessToken)
      } yield receipt
    }
  }

  def createReceipt(formBody: org.http4s.multipart.Multipart[IO], accessToken: String): IO[ReceiptEntity] = createReceiptEither(formBody, accessToken).flatMap({
    case Left(error) => IO.raiseError(new Exception(s"Creating receipt failed, response code: $error"))
    case Right(receipt) => IO.pure(receipt)
  })

  def createReceiptEither(formBody: org.http4s.multipart.Multipart[IO], accessToken: String): IO[Either[String, ReceiptEntity]] = {
    import org.http4s.circe.CirceEntityCodec._

    val headers: Headers = formBody.headers.put(org.http4s.headers.Authorization(Credentials.Token(AuthScheme.Bearer, accessToken)))
    httpClient.fetch(
      POST(
        formBody,
        org.http4s.Uri.unsafeFromString(s"$appHostPort/receipt")
      ).map(_.withHeaders(headers))
    )({
      case Status.Successful(r) => r.attemptAs[ReceiptEntity].leftMap(decodeFailure => s"Failed to decode $decodeFailure, status code: ${r.status.code}").value
      case r => IO.pure(Left(s"Failed to create, status code ${r.status.code}"))
    })
  }

  def fetchReceipt(receiptId: String, accessToken: String): IO[ReceiptEntity] = {
    import org.http4s.circe.CirceEntityCodec._

    httpClient.expect[ReceiptEntity](
      GET(
        org.http4s.Uri.unsafeFromString(s"$appHostPort/receipt/$receiptId"),
        org.http4s.headers.Authorization(Credentials.Token(AuthScheme.Bearer, accessToken))
      )
    )
  }

  def fetchReceiptFile(receiptId: String, fileId: String, accessToken: String): IO[Array[Byte]] = httpClient.expect[Array[Byte]](
    GET(
      org.http4s.Uri.unsafeFromString(s"$appHostPort/receipt/$receiptId/file/$fileId"),
      org.http4s.headers.Authorization(Credentials.Token(AuthScheme.Bearer, accessToken))
    )
  )

  def patchReceipt(receiptId: String, patch: String, accessToken: String): IO[ReceiptEntity] = {
    import org.http4s.circe.CirceEntityDecoder._

    httpClient.expect[ReceiptEntity](
      PATCH(
        patch,
        org.http4s.Uri.unsafeFromString(s"$appHostPort/receipt/$receiptId"),
        org.http4s.headers.Authorization(Credentials.Token(AuthScheme.Bearer, accessToken)),
        Accept(org.http4s.MediaType.application.json)
      )
    )
  }

  def fetchReceiptList(accessToken: String): IO[List[ReceiptEntity]] = {
    import org.http4s.circe.CirceEntityCodec._

    httpClient.expect[List[ReceiptEntity]](
      GET(
        org.http4s.Uri.unsafeFromString(s"$appHostPort/receipt"),
        org.http4s.headers.Authorization(Credentials.Token(AuthScheme.Bearer, accessToken))
      )
    )
  }

  def deleteReceipt(receiptId: String, accessToken: String): IO[String] = {

    httpClient.expect[String](
      DELETE(
        org.http4s.Uri.unsafeFromString(s"$appHostPort/receipt/$receiptId"),
        org.http4s.headers.Authorization(Credentials.Token(AuthScheme.Bearer, accessToken))
      )
    )
  }

  def createTextFileContent(content: String): org.http4s.multipart.Multipart[IO] = {

    val textContent: EntityBody[IO] = EntityEncoder[IO, String].toEntity(content).body
    org.http4s.multipart.Multipart[IO](
      Vector(
        Part.fileData("receipt", "receipt.txt", textContent, `Content-Type`(org.http4s.MediaType.application.`octet-stream`)),
        Part.formData("total", s"${ReceiptTestUtils.total.get}"),
        Part.formData("description", ReceiptTestUtils.description),
        Part.formData("transactionTime", s"${ReceiptTestUtils.transactionTime}"),
        Part.formData("tags", ReceiptTestUtils.tagsAsString)
      )
    )
  }

  def createImageFileContent: org.http4s.multipart.Multipart[IO] = {
    implicit val cs: ContextShift[IO] = IO.contextShift(global)
    val receipt = getClass.getResource("/receipt.png")

    org.http4s.multipart.Multipart[IO](
      Vector(
        Part.fileData("receipt", receipt, global, `Content-Type`(org.http4s.MediaType.image.png)),
        Part.formData("total", s"${ReceiptTestUtils.total.get}"),
        Part.formData("description", ReceiptTestUtils.description),
        Part.formData("transactionTime", s"${ReceiptTestUtils.transactionTime}"),
        Part.formData("tags", ReceiptTestUtils.tagsAsString)
      )
    )
  }

}
