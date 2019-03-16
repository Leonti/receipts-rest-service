package repository

import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.BSONDocument
import receipt.StoredFile

import scala.concurrent.Future

class StoredFileRepository extends MongoDao[StoredFile] {

  lazy val collectionFuture: Future[BSONCollection] = dbFuture.map(db => db[BSONCollection]("stored-files"))

  def save(storedFile: StoredFile): Future[StoredFile] = save(collectionFuture, storedFile)

  def deleteById(id: String): Future[Unit] = deleteById(collectionFuture, id).map(_ => ())

  def findForUserIdAndMd5(userId: String, md5: String): Future[List[StoredFile]] =
    findList(collectionFuture,
             BSONDocument(
               "userId" -> userId,
               "md5"    -> md5
             ))

  def findForUserId(userId: String): Future[List[StoredFile]] =
    findList(collectionFuture, BSONDocument("userId" -> userId))
}
