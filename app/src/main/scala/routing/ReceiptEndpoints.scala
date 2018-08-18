package routing

import cats.Monad
import io.finch._
import io.finch.syntax._
import model.{ReceiptEntity, User, UserId}
import service.ReceiptPrograms
import cats.implicits._
import com.twitter.finagle.http.Status

import scala.language.higherKinds

class ReceiptEndpoints[M[_]: ToTwitterFuture: Monad](
    auth: Endpoint[User],
    receiptPrograms: ReceiptPrograms[M]
) {

  private def optionResult[A](o: Option[A]): Output[A] = o.fold[Output[A]](Output.failure(new Exception("Entity not found"), Status.NotFound))(v => Ok(v))

  private val getReceipt: Endpoint[ReceiptEntity] =
    get(auth :: "receipt" :: path[String]) { (user: User, receiptId: String) =>
      receiptPrograms.findById(UserId(user.id), receiptId).map(optionResult)
    }

  val all = getReceipt
}
