package service

import java.io.File

import model.{FileMetadata, GenericMetadata, ImageMetadata}
import util.SimpleImageInfo

import scala.util.Try

object FileMetadataParser {

  val parse: File => FileMetadata = file => {

    val image: Option[SimpleImageInfo] = Try {
      Some(new SimpleImageInfo(file))
    } getOrElse None

    image
      .map(i => ImageMetadata(length = file.length, width = i.getWidth, height = i.getHeight))
      .getOrElse(GenericMetadata(length = file.length))
  }

}
