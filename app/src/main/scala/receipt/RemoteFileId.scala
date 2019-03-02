package receipt
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import user.UserId

case class RemoteFileId(userId: UserId, fileId: String)

object RemoteFileId {
  implicit val remoteFileIdDecoder: Decoder[RemoteFileId] = deriveDecoder
  implicit val remoteFileIdEncoder: Encoder[RemoteFileId] = deriveEncoder
}
