package repository

import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONArray, BSONDocument, BSONString}
import receipt.{FileEntity, ReceiptEntity}

import scala.concurrent.Future

class ReceiptRepository extends MongoDao[ReceiptEntity] {

  lazy val collectionFuture: Future[BSONCollection] = dbFuture.map(db => db[BSONCollection]("receipts"))

  def save(receiptEntity: ReceiptEntity): Future[ReceiptEntity] = save(collectionFuture, receiptEntity)

  def deleteById(id: String): Future[Unit] = deleteById(collectionFuture, id).map(_ => ())

  def findForUserId(userId: String): Future[List[ReceiptEntity]] =
    findList(collectionFuture, BSONDocument("userId" -> userId)).map(_.sortWith(_.timestamp > _.timestamp))

  def findByIds(ids: Seq[String]): Future[List[ReceiptEntity]] =
    findList(collectionFuture,
             BSONDocument(
               "_id" ->
                 BSONDocument("$in" -> BSONArray(ids.map(BSONString)))))
      .map(_.sortWith(_.timestamp > _.timestamp))

  def addFileToReceipt(receiptId: String, file: FileEntity): Future[Unit] =
    collectionFuture
      .flatMap(
        _.update(
          selector = BSONDocument("_id" -> receiptId),
          update = BSONDocument(
            "$push" -> BSONDocument("files"        -> file),
            "$set"  -> BSONDocument("lastModified" -> System.currentTimeMillis())
          )
        ))
      .map(_ => ())

  def findById(id: String): Future[Option[ReceiptEntity]] = find(collectionFuture, queryById(id))

}
