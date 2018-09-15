package model

import io.circe.{Decoder, Encoder, HCursor, Json}
import model.FileMetaData.FileMetadataBSONReader.FileMetadataBSONWriter
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

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
              md5    <- c.downField("md5").as[String]
              length <- c.downField("length").as[Long]
              width  <- c.downField("width").as[Int]
              height <- c.downField("height").as[Int]
            } yield
              ImageMetaData(
                length = length,
                width = width,
                height = height
              )
          case fileType =>
            for {
              md5    <- c.downField("md5").as[String]
              length <- c.downField("length").as[Long]
            } yield
              GenericMetaData(
                fileType = fileType,
                length = length
              )
        })
  }

  implicit object FileMetadataBSONReader extends BSONDocumentReader[FileMetaData] {

    def read(doc: BSONDocument): FileMetaData =
      Serialization.deserialize(
        doc, {

          doc.getAs[String]("fileType").get match {
            case "IMAGE" =>
              ImageMetaData(
                length = doc.getAs[Long]("length").get,
                width = doc.getAs[Int]("width").get,
                height = doc.getAs[Int]("height").get
              )
            case _ =>
              GenericMetaData(
                fileType = doc.getAs[String]("fileType").get,
                length = doc.getAs[Long]("length").get
              )
          }
        }
      )

    implicit object FileMetadataBSONWriter extends BSONDocumentWriter[FileMetaData] {

      def write(fileMetadata: FileMetaData): BSONDocument = {

        fileMetadata match {
          case imageMetadata: ImageMetaData =>
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
          metaData = doc.getAs[FileMetaData]("metaData").get,
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
          md5 = doc.getAs[String]("md5").get
        )
      )
  }

  implicit object StoredFileBSONWriter extends BSONDocumentWriter[StoredFile] {

    def write(storedFile: StoredFile): BSONDocument = {
      BSONDocument(
        "_id"    -> storedFile.id,
        "userId" -> storedFile.userId,
        "md5"    -> storedFile.md5
      )
    }
  }
}
