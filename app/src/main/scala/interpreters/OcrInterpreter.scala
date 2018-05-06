package interpreters

import java.io.File

import cats.~>
import model.{OcrEntity, OcrText}
import ocr.model.{OcrTextAnnotation, OcrSearchResult, OcrContent}
import ocr.service.OcrService
import ops.OcrOps._
import repository.OcrRepository

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{Uri, HttpRequest, RequestEntity, HttpMethods}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import akka.actor.ActorSystem
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.concurrent.{ExecutionContextExecutor, Future}

object OcrIntepreter {
  case class OcrConfig(ocrHost: String, apiKey: String)
}

class OcrInterpreter(ocrRepository: OcrRepository, ocrService: OcrService, ocrConfig: OcrIntepreter.OcrConfig)(
    implicit system: ActorSystem,
    executor: ExecutionContextExecutor,
    materializer: ActorMaterializer)
    extends (OcrOp ~> Future)
    with DefaultJsonProtocol {

  implicit val ocrSearchResultFormat = jsonFormat1(OcrSearchResult.apply)
  implicit val ocrContent            = jsonFormat1(OcrContent.apply)

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

  def apply[A](i: OcrOp[A]): Future[A] = i match {
    case OcrImage(file: File) => ocrService.ocrImage(file)
    case SaveOcrResult(userId: String, receiptId: String, ocrResult: OcrTextAnnotation) =>
      ocrRepository.save(OcrEntity(userId = userId, id = receiptId, result = ocrResult))
    case AddOcrToIndex(userId: String, receiptId: String, ocrText: OcrText) => addOcrToIndex(userId, receiptId, ocrText.text)
    case FindIdsByText(userId: String, query: String)                       => getOcrResults(userId, query)
  }

}
