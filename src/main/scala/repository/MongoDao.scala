package repository

import java.util.concurrent.Executors

import com.typesafe.config.ConfigFactory
import model.{ReceiptEntity, User, WithId}
import reactivemongo.api._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter, BSONObjectID}
import reactivemongo.core.nodeset.Authenticate

import scala.concurrent.{ExecutionContext, Future}

trait MongoDao[T <: WithId] extends MongoConnection {

  def save(collectionFuture: Future[BSONCollection], entity: T)(implicit reader: BSONDocumentWriter[T]): Future[T] =
    collectionFuture.flatMap(_.update(queryById(entity.id), entity, upsert = true).map(_ => entity))

  def deleteById(collectionFuture: Future[BSONCollection], id: String) = collectionFuture.flatMap(_.remove(queryById(id)))

  def find(collectionFuture: Future[BSONCollection], query: BSONDocument)(implicit reader: BSONDocumentReader[T]): Future[Option[T]] = {
    collectionFuture.flatMap(_.find(query)
      .cursor[T]()
      .collect[List]().map {
      case x::xs => Some(x)
      case _ => None
    })
  }

  def findList(collectionFuture: Future[BSONCollection], query: BSONDocument)(implicit reader: BSONDocumentReader[T]): Future[List[T]] = {
    collectionFuture.flatMap(_.find(query)
      .cursor[T]()
      .collect[List]())
  }

  def queryById(id: String) = BSONDocument("_id" -> id)
}
