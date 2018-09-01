package queue

import java.io.{PrintWriter, StringWriter}
import java.util.concurrent.Executors
import cats.effect.IO
import cats.syntax.all._
import cats.instances.list._
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import processing.{FileProcessorTagless, OcrProcessorTagless}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class QueueProcessor(queue: Queue, fileProcessor: FileProcessorTagless[IO], ocrProcessor: OcrProcessorTagless[IO]) {

  val logger = Logger(LoggerFactory.getLogger("QueueProcessor"))
  // FIXME - figure out how to pick up job in a single thread, but process in multiple
  private implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  def reserveNextJob(): IO[Unit] = {
    logger.info("Checking for new jobs")
    queue
      .reserve()
      .flatMap({
        case Some(job: ReservedJob) =>
          process(job).map(_ => 1.second).handleError(_ => 1.second)
        case None => IO.pure(10.seconds)
      })
      .flatMap(nextPickupTimeout => {
        IO.sleep(nextPickupTimeout) *> reserveNextJob()
      })
      .handleError(e => {
        logger.error(s"Exception on reserving next job $e")
        IO.sleep(10.seconds) *> reserveNextJob()
      })
  }

  private def handleJobResult(job: ReservedJob, childJobs: IO[List[QueueJob]]): IO[Unit] = {
    childJobs
      .flatMap(jobs => jobs.map(job => queue.put(job)).sequence)
      .runAsync {
        case Right(_) =>
          logger.info(s"Job finished succesfully $job")
          queue.delete(job.id)
        case Left(e) =>
          val sw = new StringWriter
          e.printStackTrace(new PrintWriter(sw))
          logger.error(s"Job failed to complete $job ${sw.toString}")
          queue.bury(job.id)
      }
  }

  private def process(job: ReservedJob): IO[Unit] = {
    logger.info(s"Processing job $job")

    job.job match {
      case receiptFileJob: ReceiptFileJob =>
        handleJobResult(job, fileProcessor.processJob(receiptFileJob))
      case ocrJob: OcrJob =>
        handleJobResult(job, ocrProcessor.processJob(ocrJob))
      case _ => throw new RuntimeException(s"Unknown job $job")
    }
  }

}
