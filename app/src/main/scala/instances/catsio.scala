package instances
import cats.effect.IO
import routing.ToScalaFuture

import scala.concurrent.Future

object catsio {
  implicit val ioToScalaFuture: ToScalaFuture[IO] = new ToScalaFuture[IO] {
    override def apply[A](f: IO[A]): Future[A] = f.unsafeToFuture()
  }
}
