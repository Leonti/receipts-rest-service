package routing

import java.io.File
import akka.stream.scaladsl._
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives._
import akka.http.scaladsl.server.directives.{FileInfo, FutureDirectives}
import akka.http.scaladsl.server.directives.MarshallingDirectives._
import akka.http.scaladsl.server.directives.RouteDirectives._
import akka.stream.scaladsl.FileIO
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

// https://github.com/akka/akka/issues/19506
object FileUploadDirective {

  def uploadedFileWithFields(requiredFields: String*): Directive1[ParsedForm] = {
    extractRequestContext.flatMap { ctx =>
      import ctx.executionContext
      import ctx.materializer

      entity(as[Multipart.FormData]).flatMap { formData =>
        val processedParts: Future[Seq[Future[Part]]] = formData.parts
          .map({ part =>
            if (part.filename.isDefined) {
              val destination = File.createTempFile("akka-http-upload", ".tmp")
              val fileInfo    = FileInfo(part.name, part.filename.get, part.entity.contentType)

              val filePart: Future[Part] = part.entity.dataBytes
                .runWith(FileIO.toPath(destination.toPath))
                .map(_ => Part(name = part.name, fileInfo = Some(fileInfo), file = Some(destination)))
              filePart
            } else {
              val fieldPart: Future[Part] =
                part.entity.toStrict(10.seconds).map(e => Part(name = part.name, stringValue = Some(e.data.utf8String)))
              fieldPart
            }
          })
          .runWith(Sink.seq)

        val partsFuture: Future[Seq[Part]] = processedParts.flatMap(Future.sequence(_))

        val parsedFormFuture = partsFuture.map(parts =>
          parts.foldLeft[ParsedForm](ParsedForm(Map.empty, Map.empty))((acc, part) => {
            part.file match {
              case Some(_) => acc.copy(files = acc.files + (part.name   -> UploadedFile(part.fileInfo.get, part.file.get)))
              case None    => acc.copy(fields = acc.fields + (part.name -> part.stringValue.get))
            }
          }))

        FutureDirectives.onComplete(parsedFormFuture).flatMap {
          case Success(parsedForm) =>
            val parsedFields = parsedForm.files.keySet ++ parsedForm.fields.keySet
            val unparsed     = requiredFields.filterNot(parsedFields.contains(_))

            if (unparsed.isEmpty) {
              provide(parsedForm)
            } else {
              reject(MissingFormFieldRejection(unparsed.head))
            }
          case Failure(ex) => failWith(ex)
        }
      }
    }
  }
}

case class UploadedFile(fileInfo: FileInfo, file: File)

case class ParsedForm(files: Map[String, UploadedFile], fields: Map[String, String])

case class Part(name: String, stringValue: Option[String] = None, fileInfo: Option[FileInfo] = None, file: Option[File] = None)
