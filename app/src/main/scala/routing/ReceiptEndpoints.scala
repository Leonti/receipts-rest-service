package routing

import cats.Monad
import io.finch._
import io.finch.syntax._
import model.{ReceiptEntity, User, UserId}
import service.{FileUploadPrograms, ReceiptPrograms}
import cats.implicits._
import com.twitter.finagle.http.Status
import com.twitter.finagle.http.exp.Multipart.FileUpload

import scala.language.higherKinds

class ReceiptEndpoints[F[_]: ToTwitterFuture: Monad](
    auth: Endpoint[User],
    receiptPrograms: ReceiptPrograms[F],
    fileUploadPrograms: FileUploadPrograms[F]
) {

  private def optionResult[A](o: Option[A]): Output[A] =
    o.fold[Output[A]](Output.failure(new Exception("Entity not found"), Status.NotFound))(v => Ok(v))

  val getReceipt: Endpoint[ReceiptEntity] =
    get(auth :: "receipt" :: path[String]) { (user: User, receiptId: String) =>
      receiptPrograms.findById(UserId(user.id), receiptId).map(optionResult)
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
          receiptPrograms.findById(UserId(user.id), "").map(optionResult)
        }
    }

  val all = createReceipt :+: getReceipt
}
