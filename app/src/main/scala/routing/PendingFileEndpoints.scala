package routing

import algebras.PendingFileAlg
import cats.Monad
import io.finch._
import io.finch.syntax._
import model.{PendingFile, User}
import cats.implicits._

class PendingFileEndpoints[F[_]: Monad: ToTwitterFuture](auth: Endpoint[User], pendingFileAlg: PendingFileAlg[F]) {

  val pendingFiles: Endpoint[List[PendingFile]] = get(auth :: "pending-file") { user:User =>
    pendingFileAlg.findPendingFileForUserId(user.id).map(Ok)
  }
}
