package repository

import java.util.concurrent.Executors
import reactivemongo.api.{DefaultDB, MongoConnectionOptions, MongoDriver, ScramSha1Authentication}
import scala.concurrent.{ExecutionContext, Future}

trait MongoConnection {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  private val database = sys.env("MONGODB_DB")
  private val servers  = Seq(sys.env("MONGODB_SERVER"))

  private val driver = new MongoDriver
  private lazy val connection = driver.connection(
    nodes = servers,
    options = MongoConnectionOptions(authMode = ScramSha1Authentication)
  )

  lazy val dbFuture: Future[DefaultDB] = connection
    .authenticate(
      db = database,
      user = sys.env("MONGODB_USER"),
      password = sys.env("MONGODB_PASSWORD")
    )
    .flatMap(authentication => connection.database(database))
}
