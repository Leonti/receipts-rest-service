package model

import java.util.Optional

import de.choffmeister.auth.common.OAuth2AccessTokenResponse
import model.FileMetadata.FileMetadataBSONReader.FileMetadataBSONWriter
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}
import spray.json.{DeserializationException, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

case class FileEntity(
                       id: String,
                       parentId: Option[String],
                       ext: String,
                       metaData: FileMetadata,
                       timestamp: Long = System.currentTimeMillis
                     )

sealed trait FileMetadata {
  def fileType: String
  def length: Long
}

case class ImageMetadata(fileType: String = "IMAGE", length: Long, width: Int, height: Int) extends FileMetadata
case class GenericMetadata(fileType: String = "UNKNOWN", length: Long) extends FileMetadata

object FileMetadataFormat extends RootJsonFormat[FileMetadata] {
  def write(fileMetadata: FileMetadata) = {
    fileMetadata match {
      case imageMetadata: ImageMetadata =>
        JsObject(
          "fileType" -> JsString(imageMetadata.fileType),
          "length" -> JsNumber(imageMetadata.length),
          "width" -> JsNumber(imageMetadata.width),
          "height" -> JsNumber(imageMetadata.height)
        )
      case _ =>
        JsObject(
          "fileType" -> JsString(fileMetadata.fileType),
          "length" -> JsNumber(fileMetadata.length)
        )
    }
  }

  def read(value: JsValue): FileMetadata =
    value.asJsObject.getFields("fileType", "length") match {
      case Seq(JsString("IMAGE"), JsNumber(length)) =>
        value.asJsObject.getFields("width", "height") match {
          case Seq(JsNumber(width), JsNumber(height)) => ImageMetadata(
            length = length.toLong,
            width = width.toInt,
            height = height.toInt
          )
          case _ =>  throw new DeserializationException("'IMAGE' file metadata should have 'width' and 'height' fields!")
        }
      case Seq(JsString(fileType), JsNumber(length)) =>
        GenericMetadata(
          fileType = fileType,
          length = length.toLong
        )
      case _ => throw new DeserializationException("File metadata should have 'fileType' and 'length' fields")
    }
}

object FileMetadata {
  implicit object FileMetadataBSONReader extends BSONDocumentReader[FileMetadata] {

    def read(doc: BSONDocument): FileMetadata = Serialization.deserialize(doc, {

      doc.getAs[String]("fileType").get match {
        case "IMAGE" => ImageMetadata(
          length = doc.getAs[Long]("length").get,
          width = doc.getAs[Int]("width").get,
          height = doc.getAs[Int]("height").get
          )
        case _ => GenericMetadata(
          fileType = doc.getAs[String]("fileType").get,
          length = doc.getAs[Long]("length").get
        )
      }
    })

    implicit object FileMetadataBSONWriter extends BSONDocumentWriter[FileMetadata] {

      def write(fileMetadata: FileMetadata): BSONDocument = {

        fileMetadata match {
          case imageMetadata:ImageMetadata =>
            BSONDocument(
              "fileType" -> imageMetadata.fileType,
              "length" -> imageMetadata.length,
              "width" -> imageMetadata.width,
              "height" -> imageMetadata.height
            )
          case _ =>
            BSONDocument(
              "fileType" -> fileMetadata.fileType,
              "length" -> fileMetadata.length
            )
        }
      }
    }
  }
}

object FileEntity {
  implicit object FileEntityBSONReader extends BSONDocumentReader[FileEntity] {

    def read(doc: BSONDocument): FileEntity = Serialization.deserialize(doc, FileEntity(
      id = doc.getAs[String]("_id").get,
      parentId = doc.getAs[String]("parentId"),
      ext = doc.getAs[String]("ext").get,
      metaData = doc.getAs[FileMetadata]("metaData").get,
      timestamp = doc.getAs[Long]("timestamp").get
    ))
  }

  implicit object FileEntityBSONWriter extends BSONDocumentWriter[FileEntity] {

    def write(fileEntity: FileEntity): BSONDocument = {
      BSONDocument(
        "_id" -> fileEntity.id,
        "parentId" -> fileEntity.parentId,
        "ext" -> fileEntity.ext,
        "metaData" -> FileMetadataBSONWriter.write(fileEntity.metaData),
        "timestamp" -> fileEntity.timestamp
      )
    }
  }
}
