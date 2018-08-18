package instances

import com.twitter.util.Future
import io.finch.syntax.ToTwitterFuture
import cats.Id

object identity {
  implicit val idToTwitterFuture: ToTwitterFuture[Id] = new ToTwitterFuture[Id] {
    def apply[A](f: Id[A]): Future[A] = Future(f)
  }
}
