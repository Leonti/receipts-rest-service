package instances
import cats.effect.IO
import com.twitter.util
import io.finch.syntax.ToTwitterFuture
import io.finch.syntax.scalaFutures._

object catsio {

  implicit def ioToTwitterFuture: ToTwitterFuture[IO] = new ToTwitterFuture[IO] {
    override def apply[A](f: IO[A]): util.Future[A] = scalaToTwitterFuture(f.unsafeToFuture())
  }
}
