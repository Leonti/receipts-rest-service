package repository

import model.PendingFile
import model.PendingFile.PendingFileId
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.BSONDocument

import scala.concurrent.Future

class PendingFileRepository extends MongoDao[PendingFile] {

  lazy val collectionFuture: Future[BSONCollection] = dbFuture.map(db => db[BSONCollection]("pending-files"))

  def save(pendingFile: PendingFile): Future[PendingFile] = save(collectionFuture, pendingFile)

  def findForUserId(userId: String): Future[List[PendingFile]] =
    findList(collectionFuture, BSONDocument("userId" -> userId))

  def deleteById(id: String): Future[Unit] = deleteById(collectionFuture, id).map(_ => ())

  def deleteAll(): Future[Unit] = collectionFuture.flatMap(_.remove(BSONDocument())).map(_ => ())

}
