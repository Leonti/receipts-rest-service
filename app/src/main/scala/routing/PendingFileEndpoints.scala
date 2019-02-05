package routing

import algebras.PendingFileAlg
import cats.Monad
import cats.effect.Effect
import io.finch._
import model.{PendingFile, User}
import cats.implicits._

class PendingFileEndpoints[F[_]: Monad](auth: Endpoint[F, User], pendingFileAlg: PendingFileAlg[F])(implicit F: Effect[F])
    extends Endpoint.Module[F] {

  val pendingFiles: Endpoint[F, List[PendingFile]] = get(auth :: "pending-file") { user: User =>
    pendingFileAlg.findPendingFileForUserId(user.id).map(Ok)
  }
}
