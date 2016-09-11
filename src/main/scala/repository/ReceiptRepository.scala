package repository

import java.util.concurrent.Executors

import model.ReceiptEntity
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.BSONDocument

import scala.concurrent.{ExecutionContext, Future}

class ReceiptRepository extends MongoDao[ReceiptEntity] {

  lazy val collectionFuture: Future[BSONCollection] = dbFuture.map(db => db[BSONCollection]("receipts"))

  def save(receiptEntity: ReceiptEntity): Future[ReceiptEntity] = save(collectionFuture, receiptEntity)

  def deleteById(id: String): Future[WriteResult] = deleteById(collectionFuture, id)

  def findForUserId(userId: String): Future[List[ReceiptEntity]] =
    findList(collectionFuture, BSONDocument("userId" -> userId)).map(_.sortWith(_.timestamp > _.timestamp))

  def findById(id: String): Future[Option[ReceiptEntity]]  = find(collectionFuture, queryById(id))

}
