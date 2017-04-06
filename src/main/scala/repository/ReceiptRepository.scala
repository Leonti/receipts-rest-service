package repository

import java.util.concurrent.Executors

import model.{FileEntity, ReceiptEntity}
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson
import reactivemongo.bson.{BSONArray, BSONDocument, BSONString}

import scala.concurrent.{ExecutionContext, Future}

class ReceiptRepository extends MongoDao[ReceiptEntity] {

  lazy val collectionFuture: Future[BSONCollection] = dbFuture.map(db => db[BSONCollection]("receipts"))

  def save(receiptEntity: ReceiptEntity): Future[ReceiptEntity] = save(collectionFuture, receiptEntity)

  def deleteById(id: String): Future[WriteResult] = deleteById(collectionFuture, id)

  def findForUserId(userId: String): Future[List[ReceiptEntity]] =
    findList(collectionFuture, BSONDocument("userId" -> userId)).map(_.sortWith(_.timestamp > _.timestamp))

  def findByIds(ids: List[String]): Future[List[ReceiptEntity]] =
    findList(collectionFuture,
      BSONDocument("_id" ->
        BSONDocument("$in" -> BSONArray(ids.map(BSONString))
      )))
      .map(_.sortWith(_.timestamp > _.timestamp))

  def addFileToReceipt(receiptId: String, file: FileEntity): Future[Unit] =
    collectionFuture.flatMap(_.update(
      selector = BSONDocument("_id" -> receiptId),
      update = BSONDocument(
        "$push" -> BSONDocument("files" -> file),
        "$set" -> BSONDocument("lastModified" -> System.currentTimeMillis())
      )
    )).map(_ => ())

  def findById(id: String): Future[Option[ReceiptEntity]]  = find(collectionFuture, queryById(id))

}
