package repository

import model.OcrEntity
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.WriteResult

import scala.concurrent.Future

class OcrRepository extends MongoDao[OcrEntity] {

  lazy val collectionFuture: Future[BSONCollection] = dbFuture.map(db => db[BSONCollection]("ocr"))

  def save(ocrEntity: OcrEntity): Future[OcrEntity] = save(collectionFuture, ocrEntity)

  def deleteById(id: String): Future[WriteResult] = deleteById(collectionFuture, id)

  def findById(id: String): Future[Option[OcrEntity]]  = find(collectionFuture, queryById(id))
}
