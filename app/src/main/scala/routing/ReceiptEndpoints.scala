package routing

import cats.Monad
import io.finch._
import io.finch.syntax._
import model.UserId
//import io.finch.syntax.scalaFutures._
import model.{PendingFile, User}
import service.ReceiptPrograms
import cats.implicits._

//import scala.concurrent.Future
import scala.language.higherKinds

class ReceiptEndpoints[M[_]: ToTwitterFuture: Monad](
                        auth: Endpoint[User],
                        receiptPrograms: ReceiptPrograms[M]
                      ) {

  val test: Endpoint[PendingFile] =
    get(auth :: "receipt" :: path[String]) { (user: User, receiptId: String) =>
      receiptPrograms.findById(UserId(user.id), receiptId).map(optReceipt => Ok(PendingFile("", user.id, receiptId)))
    }
}
