package instances
import cats.effect.IO
import com.twitter.util
import io.finch.syntax.ToTwitterFuture
import routing.ToScalaFuture
import io.finch.syntax.scalaFutures._

import scala.concurrent.Future

object catsio {
  implicit val ioToScalaFuture: ToScalaFuture[IO] = new ToScalaFuture[IO] {
    override def apply[A](f: IO[A]): Future[A] = f.unsafeToFuture()
  }

  implicit def ioToTwitterFuture: ToTwitterFuture[IO] = new ToTwitterFuture[IO] {
    override def apply[A](f: IO[A]): util.Future[A] = scalaToTwitterFuture(f.unsafeToFuture())
  }
}
