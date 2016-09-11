
import java.io.{ByteArrayInputStream, File}
import java.nio.charset.StandardCharsets
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import model.{FileEntity, GenericMetadata, ImageMetadata}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}
import service.{FileService, ImageResizingService}

import scala.concurrent.Future
import scala.io.BufferedSource
import akka.stream.scaladsl.{Source, _}

class FileServiceSpec extends FlatSpec with Matchers with MockitoSugar with ScalaFutures {

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()
  implicit val irs = new ImageResizingService()

  class MockFileService extends FileService {
    implicit val mat: Materializer = materializer

    override def save(userId: String, file: File, ext: String): Seq[Future[FileEntity]] = {
      val fileId = "1"
      Seq(Future.successful(toFileEntity(userId, None, fileId, file, ext)))
    }

    override def fetch(userId: String, fileId: String) =
      StreamConverters.fromInputStream(() => new ByteArrayInputStream("some text".getBytes))

    override def delete(userId: String, fileId: String): Future[Unit] = Future.successful(Unit)
  }

  it should "parse width and length of an image" in {

    val receiptImage: BufferedSource = scala.io.Source.fromURL(getClass.getResource("/receipt.png"), "ISO-8859-1")
    val byteString = ByteString(receiptImage.map(_.toByte).toArray)
    val source = Source[ByteString](List(byteString))
    val fileService = new MockFileService()

    val file = new File(UUID.randomUUID().toString)

    val fileEntityFuture = for {
      f <- source.runWith(FileIO.toPath(file.toPath))
      fileEntity <- fileService.save("userId", file, "png").head
    } yield fileEntity

      whenReady(fileEntityFuture) { fileEntity =>
        file.delete

        fileEntity.metaData match {
          case ImageMetadata(fileType, length, width, height) =>
            width shouldBe 50
            height shouldBe 67
            length shouldBe 5874
          case _ => fail("Metadata should be of an IMAGE type!")
        }
      }
  }

  it should "set unknown upload to GenericMetadata" in {
    val byteString = ByteString("some text".getBytes(StandardCharsets.UTF_8))
    val source = Source[ByteString](List(byteString))
    val fileService = new MockFileService()

    val file = new File(UUID.randomUUID().toString)

    val fileEntityFuture = for {
      f <- source.runWith(FileIO.toPath(file.toPath))
      fileEntity <- fileService.save("userId", file, "txt").head
    } yield fileEntity

    whenReady(fileEntityFuture) { fileEntity =>
      file.delete

      fileEntity.metaData match {
        case GenericMetadata(fileType, length) =>
          length shouldBe 9
        case _ => fail("Metadata should be of a GENERIC type!")
      }
    }
  }

}
