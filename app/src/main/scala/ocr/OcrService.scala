package ocr

import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors

import algebras.ImageAlg
import cats.effect.{ContextShift, IO}
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.vision.v1.model.{AnnotateImageRequest, BatchAnnotateImagesRequest, Feature, Image}
import com.google.api.services.vision.v1.{Vision, VisionScopes}
import com.google.common.collect.ImmutableList

import scala.concurrent.{ExecutionContext, Future}
import java.io.ByteArrayInputStream

trait OcrService {
  def ocrImage(file: File): Future[OcrTextAnnotation]
}

class GoogleOcrService(apiCredentials: String, imageResizeAlg: ImageAlg[IO]) extends OcrService {

  private implicit val ec                   = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))
  private implicit val cs: ContextShift[IO] = IO.contextShift(ec)

  def ocrImage(file: File): Future[OcrTextAnnotation] = {
    if (file.length() >= 4000000) {
      // FIXME - use logging
      println(s"File is too damn big, resizing ${file.getAbsolutePath}")
      // FIXME remove Future
      (for {
        resized    <- imageResizeAlg.resizeToFileSize(file, 3.7)
        annotation <- ocrResizedImage(resized)
        _ = resized.delete()
      } yield annotation).unsafeToFuture()
    } else ocrResizedImage(file).unsafeToFuture()
  }

  def ocrResizedImage(file: File): IO[OcrTextAnnotation] = {
    val credential =
      GoogleCredential
        .fromStream(new ByteArrayInputStream(apiCredentials.getBytes(java.nio.charset.StandardCharsets.UTF_8.name)))
        .createScoped(VisionScopes.all())

    val jsonFactory = JacksonFactory.getDefaultInstance
    val vision = new Vision.Builder(GoogleNetHttpTransport.newTrustedTransport(), jsonFactory, credential)
      .setApplicationName("Receipts")
      .build()

    val requests: ImmutableList.Builder[AnnotateImageRequest] = ImmutableList.builder()

    requests.add(
      new AnnotateImageRequest()
        .setImage(new Image().encodeContent(Files.readAllBytes(file.toPath)))
        .setFeatures(
          ImmutableList.of(
            new Feature()
              .setType("TEXT_DETECTION")
              .setMaxResults(10000)
          )
        )
    )

    val annotate =
      vision
        .images()
        .annotate(new BatchAnnotateImagesRequest().setRequests(requests.build()))

    // Due to a bug: requests to Vision API containing large images fail when GZipped.
    annotate.setDisableGZipContent(true)

    IO.fromFuture(IO(Future {
      val batchResponse = annotate.execute()

      val response = batchResponse.getResponses.get(0)

      if (response.getFullTextAnnotation == null)
        OcrTextAnnotation(text = "", pages = List())
      else
        OcrTextAnnotation.fromTextAnnotation(response.getFullTextAnnotation)
    }))
  }
}

class OcrServiceStub extends OcrService {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  def ocrImage(file: File): Future[OcrTextAnnotation] = Future {
    OcrTextAnnotation(text = "Parsed ocr text", pages = List())
  }
}
