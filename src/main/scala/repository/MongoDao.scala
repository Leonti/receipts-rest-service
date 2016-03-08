package repository

import com.typesafe.config.ConfigFactory
import model.{ReceiptEntity, User, WithId}
import reactivemongo.api._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocumentWriter, BSONDocumentReader, BSONObjectID, BSONDocument}
import reactivemongo.core.nodeset.Authenticate

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MongoDao[T <: WithId] {

  val config = ConfigFactory.load()
  val database = config.getString("mongodb.db")
  val servers = config.getStringList("mongodb.servers").asScala

  val driver = new MongoDriver
  val connection = driver.connection(
    nodes = servers,
    options = MongoConnectionOptions(authMode = ScramSha1Authentication),
    authentications = Seq(Authenticate(
      database,
      config.getString("mongodb.user"),
      config.getString("mongodb.password"))))

  val db = connection(database)

  def save(collection: BSONCollection, entity: T)(implicit reader: BSONDocumentWriter[T]): Future[T] = collection.update(queryById(entity.id), entity, upsert = true).map(_ => entity)

  def deleteById(collection: BSONCollection, id: String) = collection.remove(queryById(id))

  def find(collection: BSONCollection, query: BSONDocument)(implicit reader: BSONDocumentReader[T]): Future[Option[T]] = {
    collection.find(query)
      .cursor[T](ReadPreference.secondaryPreferred)
      .collect[List]().map {
      case x::xs => Some(x)
      case _ => None
    }
  }

  def findList(collection: BSONCollection, query: BSONDocument)(implicit reader: BSONDocumentReader[T]): Future[List[T]] = {
    collection.find(query)
      .cursor[T](ReadPreference.secondaryPreferred)
      .collect[List]()
  }

  def queryById(id: String) = BSONDocument("_id" -> id)
}
