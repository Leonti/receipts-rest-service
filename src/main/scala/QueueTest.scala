import java.util.concurrent.Executors

import queue.Models.Job
import queue._

import scala.concurrent.{ExecutionContext, Future}

object QueueTest {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(200))

  def main1(args: Array[String]): Unit = {

    val queue = new Queue()

    val receiptFileJob = ReceiptFileJob(
      filePath = "Some file",
      userId = "user id",
      receiptId = "receipt id",
      fileExt = "jpg"
    )

    val jobsAfterDelete = for {
      _ <- queue.put(receiptFileJob)
      jobs: List[Job] <- queue.list()
      reservedJob: Option[ReservedJob] <- {
        println(s"jobs on the queue ${jobs}")
        queue.reserve()
      }
      jobsAfterReserved: List[Job] <- queue.list()
      _ <- {
        println(s"jobs on the queue after reservation ${jobsAfterReserved}")
        println(s"job ${reservedJob}")
        Future.successful(())
      }
      _ <- queue.delete(reservedJob.get.id)
      jobsAfterDelete: List[Job] <- queue.list()
    } yield jobsAfterDelete

    jobsAfterDelete.onComplete(jobsTry => {
      println(s"Result ${jobsTry}")
    })

  }
}
