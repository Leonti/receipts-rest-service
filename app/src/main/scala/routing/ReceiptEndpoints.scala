package routing

import java.net.URLConnection

import cats.Monad
import io.finch._
import io.finch.circe._
import io.finch.syntax._
import model.{ReceiptEntity, User, UserId}
import service.{FileUploadPrograms, ReceiptErrors, ReceiptPrograms}
import cats.implicits._
import gnieh.diffson.circe._
import gnieh.diffson.circe.DiffsonProtocol._
import com.twitter.conversions.storage._
import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.http.Status
import com.twitter.finagle.http.exp.Multipart.FileUpload
import com.twitter.io.{Buf, Reader}
import io.finch.Error.NotPresent
import service.ReceiptErrors.Error

import scala.language.higherKinds

class ReceiptEndpoints[F[_]: ToTwitterFuture: Monad](
    auth: Endpoint[User],
    receiptPrograms: ReceiptPrograms[F],
    fileUploadPrograms: FileUploadPrograms[F]
) {

  private def optionResult[A](o: Option[A]): Output[A] =
    o.fold[Output[A]](Output.failure(new Exception("Entity not found"), Status.NotFound))(v => Ok(v))

  private def eitherResult(e: Either[Error, ReceiptEntity]): Output[ReceiptEntity] = e match {
    case Right(receipt) => Created(receipt)
    case Left(error) =>
      error match {
        case ReceiptErrors.FileAlreadyExists() =>
          Output.failure(new Exception("Can't create receipt with the same file"), Status.BadRequest)
        case ReceiptErrors.ReceiptNotFound(receiptId) =>
          Output.failure(new Exception(s"Receipt $receiptId doesn't exist"), Status.NotFound)
      }
  }

  val getReceipt: Endpoint[ReceiptEntity] =
    get(auth :: "receipt" :: path[String]) { (user: User, receiptId: String) =>
      receiptPrograms.findById(UserId(user.id), receiptId).map(optionResult)
    }

  val getReceipts: Endpoint[Seq[ReceiptEntity]] =
    get(auth :: "receipt" :: paramOption[Long]("last-modified") :: paramOption[String]("q")) {
      (user: User, lastModified: Option[Long], query: Option[String]) =>
        receiptPrograms.findForUser(UserId(user.id), lastModified, query).map(Ok)
    }

  val createReceipt: Endpoint[ReceiptEntity] =
    post(
      auth :: "receipt" :: multipartFileUpload("receipt") ::
        multipartAttribute("total") ::
        multipartAttribute("description") ::
        multipartAttribute("transactionTime") ::
        multipartAttribute("tags")) {
      (user: User, fileUpload: FileUpload, total: String, description: String, transactionTime: String, tags: String) =>
        fileUploadPrograms.toReceiptUpload(fileUpload, total, description, transactionTime, tags).flatMap { receiptUpload =>
          receiptPrograms.createReceipt(UserId(user.id), receiptUpload).map(eitherResult)
        }
    } handle {
      case e: NotPresent => Output.failure(e, Status.BadRequest)
    }

  val patchReceipt: Endpoint[ReceiptEntity] =
    patch(auth :: "receipt" :: path[String] :: jsonBody[JsonPatch]) { (user: User, receiptId: String, patch: JsonPatch) =>
      receiptPrograms.patchReceipt(UserId(user.id), receiptId, patch).map(optionResult)
    }

  val getReceiptFile: Endpoint[AsyncStream[Buf]] =
    get(auth :: "receipt" :: path[String] :: "file" :: path[String]) { (user: User, receiptId: String, fileIdWithExt: String) =>
      val fileId = fileIdWithExt.split('.')(0)

      receiptPrograms
        .receiptFileWithExtension(UserId(user.id), receiptId, fileId)
        .map(_.fold[Output[AsyncStream[Buf]]](Output.failure(new Exception("Receipt not found")))(fileToServe => {
          val mimeType = Option(URLConnection.guessContentTypeFromName("file." + fileToServe.ext)).getOrElse("application/octet-stream")
          Ok(AsyncStream.fromReader(Reader.fromStream(fileToServe.source), chunkSize = 128.kilobytes.inBytes.toInt))
            .withHeader("Content-Type", mimeType)
        }))
    }

  val deleteReceipt: Endpoint[Unit] = delete(auth :: "receipt" :: path[String]) { (user: User, receiptId: String) =>
    receiptPrograms
      .removeReceipt(UserId(user.id), receiptId)
      .map({
        case Some(_) => Output.unit(Status.NoContent)
        case None    => Output.failure(new Exception("Entity not found"), Status.NotFound)
      })
  }

  val all = createReceipt :+: getReceipt :+: getReceipts :+: getReceiptFile :+: patchReceipt :+: deleteReceipt
}
