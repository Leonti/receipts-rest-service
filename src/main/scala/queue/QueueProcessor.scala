package queue

import java.io.{PrintWriter, StringWriter}
import java.util.concurrent.Executors

import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import processing.FileProcessor

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class QueueProcessor(
                      queue: Queue,
                      fileProcessor: FileProcessor,
                      system: ActorSystem) {

  val logger = Logger(LoggerFactory.getLogger("QueueProcessor"))
  implicit val ec = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  def reserveNextJob(): Unit = {
    logger.info("Checking for new jobs")
    queue.reserve().map(reservedJobOption => {
      val nextPickupTimeout: FiniteDuration = reservedJobOption match {
        case Some(job: ReservedJob) =>
          process(job)
          1.seconds
        case None => 10.seconds
      }

      system.scheduler.scheduleOnce(nextPickupTimeout)(reserveNextJob)
    }).onFailure {
      case e: Throwable => {
        logger.error(s"Exception on reserving next job $e")
        system.scheduler.scheduleOnce(10.seconds)(reserveNextJob)
      }
    }
  }

  def process(job: ReservedJob) = {
    logger.info(s"Processing job $job")

    job.job match {
      case (receiptFileJob: ReceiptFileJob) => fileProcessor.process(receiptFileJob).onComplete{
        case Success(_) =>
          logger.info(s"Job finished succesfully $job")
          queue.delete(job.id)
        case Failure(e) =>

          val sw = new StringWriter
          e.printStackTrace(new PrintWriter(sw))
          logger.error(s"Job failed to complete $job ${sw.toString}")
          queue.bury(job.id)
      }
      case _ => throw new RuntimeException(s"Unknown job $job")
    }
  }

}
