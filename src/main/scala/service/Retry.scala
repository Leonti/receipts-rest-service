package service

import akka.actor.Scheduler

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import akka.pattern.after

trait Retry {

  def retry[T](f: => Future[T], delays: Seq[FiniteDuration])(implicit ec: ExecutionContext, s: Scheduler): Future[T] = {
    f recoverWith { case _ if delays.nonEmpty => after(delays.head, s)(retry(f, delays.tail)) }
  }

}
