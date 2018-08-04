package queue

import java.io.{PrintWriter, StringWriter}
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import processing.{FileProcessorTagless, OcrProcessorTagless}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class QueueProcessor(queue: Queue, fileProcessor: FileProcessorTagless[Future], ocrProcessor: OcrProcessorTagless[Future], system: ActorSystem) {

  val logger              = Logger(LoggerFactory.getLogger("QueueProcessor"))
  private implicit val ec = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  def reserveNextJob(): Unit = {
    //  logger.info("Checking for new jobs")
    queue
      .reserve()
      .map(reservedJobOption => {
        val nextPickupTimeout: FiniteDuration = reservedJobOption match {
          case Some(job: ReservedJob) =>
            process(job)
            1.seconds
          case None => 10.seconds
        }

        system.scheduler.scheduleOnce(nextPickupTimeout)(reserveNextJob())
      })
      .onComplete {
        case Failure(e: Throwable) =>
          logger.error(s"Exception on reserving next job $e")
          system.scheduler.scheduleOnce(10.seconds)(reserveNextJob())
        case Success(_) => ()
      }
  }

  private def handleJobResult(job: ReservedJob, childJobs: Future[List[QueueJob]]) = {
    childJobs
      .flatMap(jobs => Future.sequence(jobs.map(job => queue.put(job))))
      .onComplete {
        case Success(jobIds) =>
          logger.info(s"Job finished succesfully $job")
          queue.delete(job.id)
        case Failure(e) =>
          val sw = new StringWriter
          e.printStackTrace(new PrintWriter(sw))
          logger.error(s"Job failed to complete $job ${sw.toString}")
          queue.bury(job.id)
      }
  }

  private def process(job: ReservedJob) = {
    logger.info(s"Processing job $job")

    job.job match {
      case (receiptFileJob: ReceiptFileJob) =>
        handleJobResult(job, fileProcessor.processJob(receiptFileJob))
      case (ocrJob: OcrJob) =>
        handleJobResult(job, ocrProcessor.processJob(ocrJob))
      case _ => throw new RuntimeException(s"Unknown job $job")
    }
  }

}
