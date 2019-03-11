package queue

import java.io.{PrintWriter, StringWriter}
import algebras.QueueAlg
import cats.Monad
import cats.effect.{Effect, Timer}
import cats.syntax.all._
import cats.instances.list._
import processing.{FileProcessor, OcrProcessor}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContextExecutor

class QueueProcessor[F[_]: Effect](queueAlg: QueueAlg[F], fileProcessor: FileProcessor[F], ocrProcessor: OcrProcessor[F])
                                 (implicit val ec: ExecutionContextExecutor, timer: Timer[F]) {

  def reserveNextJob(): F[Unit] = {
    println("Checking for new jobs")
    queueAlg
      .reserve()
      .flatMap({
        case Some(job: ReservedJob) =>
          process(job).flatMap(_ => reserveNextJob())
        case None => reserveNextJob()
      })
      .handleError(e => {
        // FIXME - log error
        println(s"Exception on reserving next job $e")
        timer.sleep(10.seconds) *> reserveNextJob()
      })
  }

  private def handleJobResult(job: ReservedJob, childJobs: F[List[QueueJob]]): F[Unit] = {

    val result = for {
      jobs <- childJobs
      _ <- jobs.map(job => queueAlg.submit(job)).sequence
      _ <- queueAlg.delete(job.id)
      _ <- Monad[F].pure(println(s"Job finished successfully $job"))
    } yield ()

    result.handleError(e => {
      val sw = new StringWriter
      e.printStackTrace(new PrintWriter(sw))
      // FIXME log error
      println(s"Job failed to complete $job ${sw.toString}")
      queueAlg.bury(job.id)
    })

    result
  }

  private def process(job: ReservedJob): F[Unit] = {
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
