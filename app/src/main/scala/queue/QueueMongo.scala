package queue

import algebras.QueueAlg
import cats.effect.IO
import queue.Models._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}
import repository.MongoConnection

import scala.concurrent.Future

package object Models {
  type JobId = String

  sealed trait JobStatus {
    def asString: String
  }

  case object Ready extends JobStatus {
    val asString = "READY"
  }

  case object Reserved extends JobStatus {
    val asString = "RESERVED"
  }

  case object Buried extends JobStatus {
    val asString = "BURIED"
  }

  object JobStatus {
    def apply(asString: String): JobStatus = asString match {
      case "READY"    => Ready
      case "RESERVED" => Reserved
      case "BURIED"   => Buried
    }
  }

  case class Job(id: JobId, payload: String, status: JobStatus, created: Long, runAfter: Long)

  implicit object JobBSONWriter extends BSONDocumentWriter[Job] {

    def write(job: Job): BSONDocument =
      BSONDocument(
        "_id"      -> job.id,
        "payload"  -> job.payload,
        "status"   -> job.status.asString,
        "created"  -> job.created,
        "runAfter" -> job.runAfter
      )
  }

  implicit object JobBSONReader extends BSONDocumentReader[Job] {
    def read(doc: BSONDocument): Job =
      Job(
        id = doc.getAs[String]("_id").get,
        payload = doc.getAs[String]("payload").get,
        status = JobStatus(doc.getAs[String]("status").get),
        created = doc.getAs[Long]("created").get,
        runAfter = doc.getAs[Long]("runAfter").get
      )
  }
}

class QueueMongo extends QueueAlg[IO] with MongoConnection {

  lazy val collectionFuture: Future[BSONCollection] = dbFuture.map(db => db[BSONCollection]("queue"))

  def submit(queueJob: QueueJob): IO[Unit] = {
    val job = Job(
      id = java.util.UUID.randomUUID.toString,
      payload = QueueJob.asString(queueJob),
      status = Ready,
      created = System.currentTimeMillis,
      runAfter = System.currentTimeMillis
    )
    IO.fromFuture(IO(collectionFuture.flatMap(_.insert[Job](job).map(_ => ()))))
  }

  def reserve(): IO[Option[ReservedJob]] = {
    println("reserving a job")
    val future = collectionFuture.flatMap(
      _.findAndUpdate(
        BSONDocument(
          "status"   -> "READY",
          "runAfter" -> BSONDocument("$lt" -> System.currentTimeMillis)
        ),
        BSONDocument("$set" -> BSONDocument("status" -> "RESERVED")),
        sort = Some(BSONDocument("created" -> 1))
      ).map(_.result[Job].map { job =>
        println("RESERVED A JOB")
        ReservedJob(
          id = job.id,
          job = QueueJob.fromString(job.payload)
        )
      }))

    IO.fromFuture(IO(future))
  }

  def delete(id: JobId): IO[Unit] = IO.fromFuture(IO(collectionFuture.flatMap(_.remove(BSONDocument("_id" -> id)).map(_ => ()))))

  def release(id: JobId): IO[Unit] =
    IO.fromFuture(
      IO(
        collectionFuture.flatMap(
          _.update(
            BSONDocument("_id"  -> id),
            BSONDocument("$set" -> BSONDocument("status" -> "READY"))
          ).map(_ => ()))))

  def bury(id: JobId): IO[Unit] =
    IO.fromFuture(
      IO(
        collectionFuture.flatMap(
          _.update(
            BSONDocument("_id"  -> id),
            BSONDocument("$set" -> BSONDocument("status" -> "BURIED"))
          ).map(_ => ()))))
}
