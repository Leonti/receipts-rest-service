package repository

import model.ReceiptEntity
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.BSONDocument

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReceiptRepository extends MongoDao[ReceiptEntity] {
  val collection: BSONCollection = db[BSONCollection]("receipts")

  def save(receiptEntity: ReceiptEntity): Future[ReceiptEntity] = save(collection, receiptEntity)

  def deleteById(id: String): Future[WriteResult] = deleteById(collection, id)

  def findForUserId(userId: String): Future[List[ReceiptEntity]] =
    findList(collection, BSONDocument("userId" -> userId)).map(_.sortWith(_.timestamp > _.timestamp))

  def findById(id: String): Future[Option[ReceiptEntity]]  = find(collection, queryById(id))

}
