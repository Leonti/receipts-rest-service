package repository

import java.util.concurrent.Executors

import com.typesafe.config.ConfigFactory
import reactivemongo.api.{DefaultDB, MongoConnectionOptions, MongoDriver, ScramSha1Authentication}
import reactivemongo.core.nodeset.Authenticate

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

trait MongoConnection {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  private val config   = ConfigFactory.load()
  private val database = config.getString("mongodb.db")
  private val servers  = config.getStringList("mongodb.servers").asScala

  private val driver = new MongoDriver
  private lazy val connection = driver.connection(
    nodes = servers,
    options = MongoConnectionOptions(authMode = ScramSha1Authentication)
  )

  lazy val dbFuture: Future[DefaultDB] = connection
    .authenticate(
      db = database,
      user = config.getString("mongodb.user"),
      password = config.getString("mongodb.password")
    )
    .flatMap(authentication => connection.database(database))
}
