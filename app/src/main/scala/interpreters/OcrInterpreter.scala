package interpreters

import java.io.File

import model.{OcrEntity, OcrText}
import ocr.model.{OcrContent, OcrSearchResult, OcrTextAnnotation}
import ocr.service.OcrService
import algebras.OcrAlg
import repository.OcrRepository
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import akka.actor.ActorSystem
import cats.effect.IO
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import scala.concurrent.{ExecutionContextExecutor, Future}

object OcrIntepreter {
  case class OcrConfig(ocrHost: String, apiKey: String)
}

class OcrInterpreterTagless(ocrRepository: OcrRepository, ocrService: OcrService, ocrConfig: OcrIntepreter.OcrConfig)(
    implicit system: ActorSystem,
    executor: ExecutionContextExecutor,
    materializer: ActorMaterializer)
    extends OcrAlg[IO] {

  def getOcrResults(userId: String, query: String): Future[Seq[String]] =
    for {
      response <- Http().singleRequest(
        HttpRequest(
          uri = Uri(s"${ocrConfig.ocrHost}/api/search/$userId").withQuery(Uri.Query("q" -> query)),
          headers = List(RawHeader("Authorization", s"ApiKey ${ocrConfig.apiKey}"))
        ))
      ocrSearchResult <- Unmarshal(response.entity).to[OcrSearchResult]
    } yield ocrSearchResult.ids

  def addOcrToIndex(userId: String, receiptId: String, content: String): Future[Unit] =
    for {
      request <- Marshal(OcrContent(content)).to[RequestEntity]
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"${ocrConfig.ocrHost}/api/search/$userId/$receiptId",
          headers = List(RawHeader("Authorization", s"ApiKey ${ocrConfig.apiKey}")),
          entity = request
        ))
    } yield ()

  override def ocrImage(file: File): IO[OcrTextAnnotation] = IO.fromFuture(IO(ocrService.ocrImage(file)))
  override def saveOcrResult(userId: String, receiptId: String, ocrResult: OcrTextAnnotation): IO[OcrEntity] =
    IO.fromFuture(IO(ocrRepository.save(OcrEntity(userId = userId, id = receiptId, result = ocrResult))))
  override def addOcrToIndex(userId: String, receiptId: String, ocrText: OcrText): IO[Unit] =
    IO.fromFuture(IO(addOcrToIndex(userId, receiptId, ocrText.text)))
  override def findIdsByText(userId: String, query: String): IO[Seq[String]] =
    IO.fromFuture(IO(getOcrResults(userId, query)))
}
