import java.io.FileInputStream
import java.io.File

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.vision.v1.model._
import com.google.api.services.vision.v1.{Vision, VisionScopes}
import com.google.common.collect.ImmutableList
import java.nio.file.{Files}

import ocr.model.OcrTextAnnotation

object OcrTest {

  def main2(args: Array[String]): Unit = {
    val credential =
      GoogleCredential
        .fromStream(new FileInputStream(new File("/home/leonti/development/google-cloud/Receipts-dev-ocr-service-account.json")))
        .createScoped(VisionScopes.all())

    val jsonFactory = JacksonFactory.getDefaultInstance
    val vision = new Vision.Builder(GoogleNetHttpTransport.newTrustedTransport(), jsonFactory, credential)
      .setApplicationName("Receipts")
      .build()

    val requests: ImmutableList.Builder[AnnotateImageRequest] = ImmutableList.builder()

    requests.add(
      new AnnotateImageRequest()
        .setImage(
          new Image().encodeContent(Files.readAllBytes(new File("/home/leonti/development/document-scanner/images/7.jpg").toPath)))
        .setFeatures(ImmutableList.of(new Feature()
          .setType("TEXT_DETECTION")
          .setMaxResults(100))))

    val annotate =
      vision
        .images()
        .annotate(new BatchAnnotateImagesRequest().setRequests(requests.build()))
    // Due to a bug: requests to Vision API containing large images fail when GZipped.
    annotate.setDisableGZipContent(true)
    val batchResponse = annotate.execute()

    val response = batchResponse.getResponses().get(0)

    val annotations = response.getTextAnnotations

    println(response.getFullTextAnnotation.toPrettyString)
    val fullAnnotation = OcrTextAnnotation.fromTextAnnotation(response.getFullTextAnnotation)

    println(fullAnnotation)
  }

}
