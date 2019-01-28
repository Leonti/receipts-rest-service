package queue

import java.io.{PrintWriter, StringWriter}
import java.util.concurrent.Executors

import cats.effect.{IO, Timer}
import cats.syntax.all._
import cats.instances.list._
import processing.{FileProcessor, OcrProcessor}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class QueueProcessor(queue: Queue, fileProcessor: FileProcessor[IO], ocrProcessor: OcrProcessor[IO]) {

  // FIXME - figure out how to pick up job in a single thread, but process in multiple
  private implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
  private implicit val timer: Timer[IO] = IO.timer(ec)

  def reserveNextJob(): IO[Unit] = {
    println("Checking for new jobs")
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
        // FIXME - log error
        println(s"Exception on reserving next job $e")
        IO.sleep(10.seconds) *> reserveNextJob()
      })
  }

  private def handleJobResult(job: ReservedJob, childJobs: IO[List[QueueJob]]): IO[Unit] = {
    childJobs
      .flatMap(jobs => jobs.map(job => queue.put(job)).sequence)
      .runAsync {
        case Right(_) =>
          println(s"Job finished succesfully $job")
          queue.delete(job.id)
        case Left(e) =>
          val sw = new StringWriter
          e.printStackTrace(new PrintWriter(sw))
          // FXIME log error
          println(s"Job failed to complete $job ${sw.toString}")
          queue.bury(job.id)
      }.toIO
  }

  private def process(job: ReservedJob): IO[Unit] = {
    println(s"Processing job $job")

    job.job match {
      case receiptFileJob: ReceiptFileJob =>
        handleJobResult(job, fileProcessor.processJob(receiptFileJob))
      case ocrJob: OcrJob =>
        handleJobResult(job, ocrProcessor.processJob(ocrJob))
      case _ => throw new RuntimeException(s"Unknown job $job")
    }
  }

}
