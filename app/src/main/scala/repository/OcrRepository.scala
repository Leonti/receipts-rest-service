package repository

import ocr.{OcrEntity, OcrTextOnly}
import reactivemongo.api.Cursor
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.{BSONDocument, BSONRegex}

import scala.concurrent.Future

class OcrRepository extends MongoDao[OcrEntity] {

  lazy val collectionFuture: Future[BSONCollection] = dbFuture.map(db => db[BSONCollection]("ocr"))

  def save(ocrEntity: OcrEntity): Future[OcrEntity] = save(collectionFuture, ocrEntity)

  def deleteById(id: String): Future[WriteResult] = deleteById(collectionFuture, id)

  def findById(id: String): Future[Option[OcrEntity]] = find(collectionFuture, queryById(id))

  def findByUserId(userId: String): Future[List[OcrEntity]] =
    findList(collectionFuture, BSONDocument("userId" -> userId))

  def findTextOnlyForUserId(userId: String, query: String): Future[List[OcrTextOnly]] =
    collectionFuture.flatMap(
      _.find(BSONDocument(
               "userId"      -> userId,
               "result.text" -> BSONRegex(query, "i")
             ),
             BSONDocument("result.text" -> 1))
        .cursor[OcrTextOnly]()
        .collect[List](-1, Cursor.FailOnError[List[OcrTextOnly]]()))
}
