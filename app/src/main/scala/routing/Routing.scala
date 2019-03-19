package routing
import algebras._
import authentication.{BearerAuth, OauthEndpoints, PathToken}
import backup.{BackupEndpoints, BackupService}
import cats.Id
import cats.effect.{ConcurrentEffect, ContextShift}
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.server.middleware.CORS
import pending.PendingFileEndpoints
import receipt.{ReceiptEndpoints, ReceiptPrograms}
import user.{UserEndpoints, UserPrograms}

import scala.concurrent.ExecutionContext

case class RoutingAlgebras[F[_]](
    jwtVerificationAlg: JwtVerificationAlg[Id],
    userAlg: UserAlg[F],
    randomAlg: RandomAlg[F],
    receiptStoreAlg: ReceiptStoreAlg[F],
    localFileAlg: LocalFileAlg[F],
    remoteFileAlg: RemoteFileAlg[F],
    fileStoreAlg: FileStoreAlg[F],
    pendingFileAlg: PendingFileAlg[F],
    queueAlg: QueueAlg[F],
    ocrAlg: OcrAlg[F]
)

case class RoutingConfig(
    uploadsFolder: String,
    googleClientId: String,
    authTokenSecret: Array[Byte]
)

class Routing[F[_]: ConcurrentEffect: ContextShift](algebras: RoutingAlgebras[F], config: RoutingConfig, bec: ExecutionContext) {
  import algebras._

  private val userPrograms = new UserPrograms[F](userAlg, randomAlg)

  private val auth = new BearerAuth[F](
    jwtVerificationAlg,
    subClaim => userPrograms.findUserByExternalId(subClaim.value)
  )

  private val receiptPrograms = new ReceiptPrograms[F](
    config.uploadsFolder,
    receiptStoreAlg,
    localFileAlg,
    remoteFileAlg,
    fileStoreAlg,
    pendingFileAlg,
    queueAlg,
    randomAlg,
    ocrAlg
  )

  private val receiptEndpoints =
    new ReceiptEndpoints[F](receiptPrograms)

  private val pendingFileEndpoints = new PendingFileEndpoints[F](pendingFileAlg)

  private val userEndpoints      = new UserEndpoints[F]()
  private val appConfigEndpoints = new AppConfigEndpoints[F](config.googleClientId)
  private val oauthEndpoints     = new OauthEndpoints[F](userPrograms)

  private val backupEndpoints =
    new BackupEndpoints[F](new BackupService[F](receiptStoreAlg, remoteFileAlg, bec), new PathToken(config.authTokenSecret))

  private val versionEndpoint =
    new VersionEndpoint[F]("latest")

  private val authedRoutes = receiptEndpoints.authedRoutes <+>
    pendingFileEndpoints.authedRoutes <+>
    userEndpoints.authedRoutes <+>
    backupEndpoints.authedRoutes
  private val publicRoutes = oauthEndpoints.routes <+>
    backupEndpoints.routes <+>
    appConfigEndpoints.routes <+>
    versionEndpoint.routes

  val routes: HttpRoutes[F] = CORS(publicRoutes <+> auth.authMiddleware(authedRoutes))
}
