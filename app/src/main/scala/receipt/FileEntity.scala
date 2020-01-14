package receipt

import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import model.WithId

case class FileEntity(
    id: String,
    parentId: Option[String],
    ext: String,
    metaData: FileMetaData,
    timestamp: Long
)

case class StoredFile(
    userId: String,
    id: String,
    md5: String
) extends WithId

sealed trait FileMetaData {
  def fileType: String
  def length: Long
}

case class ImageMetaData(fileType: String = "IMAGE", length: Long, width: Int, height: Int) extends FileMetaData
case class GenericMetaData(fileType: String = "UNKNOWN", length: Long)                      extends FileMetaData

object FileMetaData {

  implicit val encodeMetadata: Encoder[FileMetaData] = new Encoder[FileMetaData] {
    final def apply(a: FileMetaData): Json = a match {
      case imageMetadata: ImageMetaData =>
        Json.obj(
          ("fileType", Json.fromString(imageMetadata.fileType)),
          ("length", Json.fromLong(imageMetadata.length)),
          ("width", Json.fromInt(imageMetadata.width)),
          ("height", Json.fromInt(imageMetadata.height))
        )
      case _ =>
        Json.obj(
          ("fileType", Json.fromString(a.fileType)),
          ("length", Json.fromLong(a.length))
        )
    }
  }

  implicit val decodeMetadata: Decoder[FileMetaData] = new Decoder[FileMetaData] {
    final def apply(c: HCursor): Decoder.Result[FileMetaData] =
      c.downField("fileType")
        .as[String]
        .flatMap({
          case "IMAGE" =>
            for {
              length <- c.downField("length").as[Long]
              width  <- c.downField("width").as[Int]
              height <- c.downField("height").as[Int]
            } yield ImageMetaData(
              length = length,
              width = width,
              height = height
            )
          case fileType =>
            for {
              length <- c.downField("length").as[Long]
            } yield GenericMetaData(
              fileType = fileType,
              length = length
            )
        })
  }

}

object FileEntity {

  implicit val fileEntityDecoder: Decoder[FileEntity] = deriveDecoder
  implicit val fileEntityEncoder: Encoder[FileEntity] = deriveEncoder

}
