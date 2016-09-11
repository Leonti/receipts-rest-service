package repository

import model.PendingFile
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.BSONDocument

import scala.concurrent.Future

class PendingFileRepository extends MongoDao[PendingFile] {

  lazy val collectionFuture: Future[BSONCollection] = dbFuture.map(db => db[BSONCollection]("pending-files"))

  def save(pendingFile: PendingFile): Future[PendingFile] = save(collectionFuture, pendingFile)

  def findForUserId(userId: String): Future[List[PendingFile]] =
    findList(collectionFuture, BSONDocument("userId" -> userId))

  def deleteByReceiptId(receiptId: String): Future[Unit] =
    collectionFuture.flatMap(_.remove(BSONDocument("receiptId" -> receiptId))).map(_ => ())

  def deleteAll(): Future[Unit] = collectionFuture.flatMap(_.remove(BSONDocument())).map(_ => ())

}
