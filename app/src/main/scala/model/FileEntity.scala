package model

import io.circe.{Decoder, Encoder, HCursor, Json}
import model.FileMetadata.FileMetadataBSONReader.FileMetadataBSONWriter
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class FileEntity(
    id: String,
    parentId: Option[String],
    ext: String,
    md5: Option[String],
    metaData: FileMetadata,
    timestamp: Long = System.currentTimeMillis
)

case class StoredFile(
    userId: String,
    id: String,
    md5: String,
    size: Long
) extends WithId

sealed trait FileMetadata {
  def fileType: String
  def length: Long
}

case class ImageMetadata(fileType: String = "IMAGE", length: Long, width: Int, height: Int) extends FileMetadata
case class GenericMetadata(fileType: String = "UNKNOWN", length: Long)                      extends FileMetadata

object FileMetadata {

  implicit val encodeMetadata: Encoder[FileMetadata] = new Encoder[FileMetadata] {
    final def apply(a: FileMetadata): Json = a match {
      case imageMetadata: ImageMetadata =>
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

  implicit val decodeMetadata: Decoder[FileMetadata] = new Decoder[FileMetadata] {
    final def apply(c: HCursor): Decoder.Result[FileMetadata] =
      c.downField("fileType").as[String].flatMap({
        case "IMAGE" => for {
          length <- c.downField("length").as[Long]
          width <- c.downField("width").as[Int]
          height <- c.downField("height").as[Int]
        } yield ImageMetadata(
          length = length,
          width = width,
          height = height
        )
        case fileType => c.downField("length").as[Long].map(length => GenericMetadata(
          fileType = fileType,
          length = length
        ))
      })
  }

  implicit object FileMetadataBSONReader extends BSONDocumentReader[FileMetadata] {

    def read(doc: BSONDocument): FileMetadata =
      Serialization.deserialize(
        doc, {

          doc.getAs[String]("fileType").get match {
            case "IMAGE" =>
              ImageMetadata(
                length = doc.getAs[Long]("length").get,
                width = doc.getAs[Int]("width").get,
                height = doc.getAs[Int]("height").get
              )
            case _ =>
              GenericMetadata(
                fileType = doc.getAs[String]("fileType").get,
                length = doc.getAs[Long]("length").get
              )
          }
        }
      )

    implicit object FileMetadataBSONWriter extends BSONDocumentWriter[FileMetadata] {

      def write(fileMetadata: FileMetadata): BSONDocument = {

        fileMetadata match {
          case imageMetadata: ImageMetadata =>
            BSONDocument(
              "fileType" -> imageMetadata.fileType,
              "length"   -> imageMetadata.length,
              "width"    -> imageMetadata.width,
              "height"   -> imageMetadata.height
            )
          case _ =>
            BSONDocument(
              "fileType" -> fileMetadata.fileType,
              "length"   -> fileMetadata.length
            )
        }
      }
    }
  }
}

object FileEntity {

  implicit val fileEntityDecoder: Decoder[FileEntity] = deriveDecoder
  implicit val fileEntityEncoder: Encoder[FileEntity] = deriveEncoder

  implicit object FileEntityBSONReader extends BSONDocumentReader[FileEntity] {

    def read(doc: BSONDocument): FileEntity =
      Serialization.deserialize(
        doc,
        FileEntity(
          id = doc.getAs[String]("_id").get,
          parentId = doc.getAs[String]("parentId"),
          ext = doc.getAs[String]("ext").get,
          md5 = doc.getAs[String]("md5"),
          metaData = doc.getAs[FileMetadata]("metaData").get,
          timestamp = doc.getAs[Long]("timestamp").get
        )
      )
  }

  implicit object FileEntityBSONWriter extends BSONDocumentWriter[FileEntity] {

    def write(fileEntity: FileEntity): BSONDocument = {
      BSONDocument(
        "_id"       -> fileEntity.id,
        "parentId"  -> fileEntity.parentId,
        "ext"       -> fileEntity.ext,
        "md5"       -> fileEntity.md5,
        "metaData"  -> FileMetadataBSONWriter.write(fileEntity.metaData),
        "timestamp" -> fileEntity.timestamp
      )
    }
  }
}

object StoredFile {
  implicit object StoredFileBSONReader extends BSONDocumentReader[StoredFile] {

    def read(doc: BSONDocument): StoredFile =
      Serialization.deserialize(
        doc,
        StoredFile(
          id = doc.getAs[String]("_id").get,
          userId = doc.getAs[String]("userId").get,
          md5 = doc.getAs[String]("md5").get,
          size = doc.getAs[Long]("size").get,
        )
      )
  }

  implicit object StoredFileBSONWriter extends BSONDocumentWriter[StoredFile] {

    def write(storedFile: StoredFile): BSONDocument = {
      BSONDocument(
        "_id"    -> storedFile.id,
        "userId" -> storedFile.userId,
        "md5"    -> storedFile.md5,
        "size"   -> storedFile.size
      )
    }
  }
}
