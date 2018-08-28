package routing

import scala.concurrent.Future
import scala.language.higherKinds

trait ToScalaFuture[F[_]] {
  def apply[A](f: F[A]): Future[A]
}

object ToScalaFuture {
  implicit val identity: ToScalaFuture[Future] = new ToScalaFuture[Future] {
    override def apply[A](f: Future[A]): Future[A] = f
  }
}
