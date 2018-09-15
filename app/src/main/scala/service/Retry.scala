package service

import cats.effect._
import cats.syntax.all._
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import cats.effect.IO

object Retry {

  def retry[T](ioa: => IO[T], delays: Seq[FiniteDuration])(implicit ec: ExecutionContext): IO[T] = {

    ioa.handleErrorWith { error =>
      if (delays.nonEmpty) {
        println("Failed, retrying") // FIXME - remove
        IO.sleep(delays.head) *> retry(ioa, delays.tail)
      } else {
        IO.raiseError(error)
      }
    }
  }

}
