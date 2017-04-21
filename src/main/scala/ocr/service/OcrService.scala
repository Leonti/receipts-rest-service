package ocr.service

import java.io.{File, FileInputStream}
import java.nio.file.Files
import java.util.concurrent.Executors

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.vision.v1.model.{AnnotateImageRequest, BatchAnnotateImagesRequest, Feature, Image}
import com.google.api.services.vision.v1.{Vision, VisionScopes}
import com.google.common.collect.ImmutableList
import com.typesafe.scalalogging.Logger
import ocr.model.OcrTextAnnotation
import org.slf4j.LoggerFactory
import service.ImageResizingService

import scala.concurrent.{ExecutionContext, Future}

trait OcrService {
  def ocrImage(file: File): Future[OcrTextAnnotation]
}

class GoogleOcrService(credentialsFile: File, imageResizeService: ImageResizingService) extends OcrService {

  private implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))
  val logger              = Logger(LoggerFactory.getLogger("GoogleOcrService"))

  def ocrImage(file: File): Future[OcrTextAnnotation] = {
    if (file.length() >= 4000000) {
      logger.info(s"File is too damn big, resizing ${file.getAbsolutePath}")

      for {
        resized    <- imageResizeService.resizeToSize(file, 3.7)
        annotation <- ocrResizedImage(resized)
        _ = resized.delete()
      } yield annotation
    } else ocrResizedImage(file)
  }

  def ocrResizedImage(file: File): Future[OcrTextAnnotation] = {
    val credential =
      GoogleCredential
        .fromStream(new FileInputStream(credentialsFile))
        .createScoped(VisionScopes.all())

    val jsonFactory = JacksonFactory.getDefaultInstance
    val vision = new Vision.Builder(GoogleNetHttpTransport.newTrustedTransport(), jsonFactory, credential)
      .setApplicationName("Receipts")
      .build()

    val requests: ImmutableList.Builder[AnnotateImageRequest] = ImmutableList.builder()

    requests.add(
      new AnnotateImageRequest()
        .setImage(new Image().encodeContent(Files.readAllBytes(file.toPath)))
        .setFeatures(ImmutableList.of(new Feature()
          .setType("TEXT_DETECTION")
          .setMaxResults(10000))))

    val annotate =
      vision
        .images()
        .annotate(new BatchAnnotateImagesRequest().setRequests(requests.build()))

    // Due to a bug: requests to Vision API containing large images fail when GZipped.
    annotate.setDisableGZipContent(true)

    Future {
      val batchResponse = annotate.execute()

      val response = batchResponse.getResponses.get(0)
      OcrTextAnnotation.fromTextAnnotation(response.getFullTextAnnotation)
    }
  }
}

class OcrServiceStub extends OcrService {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  def ocrImage(file: File): Future[OcrTextAnnotation] = Future {
    OcrTextAnnotation(text = "Parsed ocr text", pages = List())
  }
}
