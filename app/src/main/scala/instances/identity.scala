package instances

import com.twitter.util.Future

import scala.concurrent.{Future => ScalaFuture}
import io.finch.syntax.ToTwitterFuture
import cats.Id
import routing.ToScalaFuture

object identity {
  implicit val idToTwitterFuture: ToTwitterFuture[Id] = new ToTwitterFuture[Id] {
    def apply[A](f: Id[A]): Future[A] = Future(f)
  }

  implicit val idToScalaFuture: ToScalaFuture[Id] = new ToScalaFuture[Id] {
    override def apply[A](f: Id[A]): ScalaFuture[A] = ScalaFuture.successful(f)
  }
}
