package ocr

import com.google.api.services.vision.v1.model.TextAnnotation
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.generic.auto._

import collection.JavaConverters._
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

  implicit val ocrContentDecoder: Decoder[OcrTextAnnotation] = deriveDecoder
  implicit val ocrContentEncoder: Encoder[OcrTextAnnotation] = deriveEncoder

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
