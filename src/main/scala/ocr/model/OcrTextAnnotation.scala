package ocr.model

import com.google.api.services.vision.v1.model.TextAnnotation
import model.{OcrEntity, Serialization}

import collection.JavaConverters._
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter, Macros, document}
case class DetectedLanguage(languageCode: String)
case class TextProperty(detectedLanguages: Seq[DetectedLanguage])
case class Vertex(x: Option[Int], y: Option[Int])
case class BoundingPoly(vertices: Seq[Vertex])
case class Symbol(boundingBox: BoundingPoly, property: TextProperty, text: String)
case class Word(boundingBox: BoundingPoly, property: TextProperty, symbols: Seq[Symbol])
case class Paragraph(boundingBox: BoundingPoly, property: TextProperty, words: Seq[Word])
case class Block(blockType: String, boundingBox: BoundingPoly, paragraphs: Seq[Paragraph], property: TextProperty)
case class Page(height: Int, width: Int, property: TextProperty, blocks: Seq[Block])
case class OcrTextAnnotation(pages: Seq[Page], text: String)

object OcrTextAnnotation {

  implicit def detectedLanguageWriter: BSONDocumentWriter[DetectedLanguage] = Macros.writer[DetectedLanguage]
  implicit def textPropertyWriter: BSONDocumentWriter[TextProperty]         = Macros.writer[TextProperty]

  implicit object VertexWriter extends BSONDocumentWriter[Vertex] {
    def write(vertex: Vertex): BSONDocument = {
      BSONDocument(
        "x" -> vertex.x.getOrElse(-1),
        "y" -> vertex.y.getOrElse(-1)
      )
    }
  }

  implicit def boundingPolyWriter: BSONDocumentWriter[BoundingPoly]           = Macros.writer[BoundingPoly]
  implicit def symbolWriter: BSONDocumentWriter[Symbol]                       = Macros.writer[Symbol]
  implicit def wordWriter: BSONDocumentWriter[Word]                           = Macros.writer[Word]
  implicit def paragraphWriter: BSONDocumentWriter[Paragraph]                 = Macros.writer[Paragraph]
  implicit def blockWriter: BSONDocumentWriter[Block]                         = Macros.writer[Block]
  implicit def pageWriter: BSONDocumentWriter[Page]                           = Macros.writer[Page]
  implicit def ocrTextAnnotationWriter: BSONDocumentWriter[OcrTextAnnotation] = Macros.writer[OcrTextAnnotation]

  implicit def detectedLanguageReader: BSONDocumentReader[DetectedLanguage] = Macros.reader[DetectedLanguage]
  implicit def textPropertyReader: BSONDocumentReader[TextProperty]         = Macros.reader[TextProperty]

  implicit object VertexBSONReader extends BSONDocumentReader[Vertex] {

    def read(doc: BSONDocument): Vertex =
      Serialization.deserialize(
        doc,
        Vertex(
          x = if (doc.getAs[Int]("x").get == -1) None else Some(doc.getAs[Int]("x").get),
          y = if (doc.getAs[Int]("y").get == -1) None else Some(doc.getAs[Int]("y").get)
        )
      )
  }

  implicit def boundingPolyReader: BSONDocumentReader[BoundingPoly]           = Macros.reader[BoundingPoly]
  implicit def symbolReader: BSONDocumentReader[Symbol]                       = Macros.reader[Symbol]
  implicit def wordReader: BSONDocumentReader[Word]                           = Macros.reader[Word]
  implicit def paragraphReader: BSONDocumentReader[Paragraph]                 = Macros.reader[Paragraph]
  implicit def blockReader: BSONDocumentReader[Block]                         = Macros.reader[Block]
  implicit def pageReader: BSONDocumentReader[Page]                           = Macros.reader[Page]
  implicit def ocrTextAnnotationReader: BSONDocumentReader[OcrTextAnnotation] = Macros.reader[OcrTextAnnotation]

  def fromTextAnnotation(textAnnotation: TextAnnotation): OcrTextAnnotation = {

    def toDetectedLanguage(dl: com.google.api.services.vision.v1.model.DetectedLanguage): DetectedLanguage = {
      DetectedLanguage(languageCode = dl.getLanguageCode)
    }

    def toTextProperty(p: com.google.api.services.vision.v1.model.TextProperty): TextProperty = {
      TextProperty(detectedLanguages = p.getDetectedLanguages.asScala.map(toDetectedLanguage))
    }

    def toVertex(vertex: com.google.api.services.vision.v1.model.Vertex): Vertex = {
      Vertex(
        x = if (vertex.getX == null) None else Option(vertex.getX),
        y = if (vertex.getY == null) None else Option(vertex.getY)
      )
    }

    def toBoundingPoly(boundingBox: com.google.api.services.vision.v1.model.BoundingPoly): BoundingPoly = {
      BoundingPoly(vertices = boundingBox.getVertices.asScala.map(toVertex))
    }

    def toSymbol(s: com.google.api.services.vision.v1.model.Symbol): Symbol = {
      Symbol(boundingBox = toBoundingPoly(s.getBoundingBox), property = toTextProperty(s.getProperty), text = s.getText)
    }

    def toWord(word: com.google.api.services.vision.v1.model.Word): Word = {
      Word(boundingBox = toBoundingPoly(word.getBoundingBox),
           property = toTextProperty(word.getProperty),
           symbols = word.getSymbols.asScala.map(toSymbol))
    }

    def toParagraph(paragraph: com.google.api.services.vision.v1.model.Paragraph): Paragraph = {
      Paragraph(boundingBox = toBoundingPoly(paragraph.getBoundingBox),
                property = toTextProperty(paragraph.getProperty),
                words = paragraph.getWords.asScala.map(toWord))
    }

    def toBlock(b: com.google.api.services.vision.v1.model.Block): Block = {
      Block(
        blockType = b.getBlockType,
        boundingBox = toBoundingPoly(b.getBoundingBox),
        paragraphs = b.getParagraphs.asScala.map(toParagraph),
        property = toTextProperty(b.getProperty)
      )
    }

    def toPage(page: com.google.api.services.vision.v1.model.Page): Page = {
      Page(height = page.getHeight,
           width = page.getWidth,
           property = toTextProperty(page.getProperty),
           blocks = page.getBlocks.asScala.map(toBlock))
    }

    OcrTextAnnotation(text = textAnnotation.getText, pages = textAnnotation.getPages.asScala.map(toPage))
  }
}
