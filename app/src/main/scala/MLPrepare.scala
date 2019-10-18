import java.io.File

import scala.io.Source
import io.circe.parser.decode
import ocr.{OcrTextAnnotation, Vertex, Word}

object MLPrepare extends App {

  val folder = "/home/leonti/Downloads/receipts/"

  def getListOfFiles(dir: String): List[File] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(_.isFile).toList
    } else {
      List[File]()
    }
  }

  val receiptIds: List[String] = getListOfFiles(s"${folder}images").map(_.getName)

  case class Point(x: Int, y: Int)

  def findCenter(vertices: List[Vertex]): Point = {
    val lowestX  = vertices.minBy(_.x.get).x.get
    val highestX = vertices.maxBy(_.x.get).x.get

    val lowestY  = vertices.minBy(_.y.get).y.get
    val highestY = vertices.maxBy(_.y.get).y.get

    Point(highestX - lowestX, highestY - lowestY)
  }

  case class Found(centerX: Int, centerY: Int)

  receiptIds.foreach(receiptId => {
    val total = BigDecimal(Source.fromFile(s"${folder}total/$receiptId").getLines.mkString)
    println(s"=============================TOTAL: $total=========================================")

    val ocr = decode[OcrTextAnnotation](Source.fromFile(s"${folder}ocr/$receiptId").getLines.mkString).toOption.get

    val words: Seq[Word] = for {
      page      <- ocr.pages
      block     <- page.blocks
      paragraph <- block.paragraphs
      word      <- paragraph.words
    } yield word

    val wordsWithBoxes = words
      .map(word => (word.symbols.map(_.text).mkString, word.boundingBox))

    val maybeTotalWord = wordsWithBoxes
      .find(wordTuple => wordTuple._1.toLowerCase == "total" || wordTuple._1.toLowerCase == "total:")
      .map(wordTuple => findCenter(wordTuple._2.vertices.toList))

    val maybeFound = wordsWithBoxes
      .find(wordTuple => wordTuple._1.contains(total.toString))
      .map(wordTuple => findCenter(wordTuple._2.vertices.toList))

    maybeFound.foreach(point => println(s"FOUND: $point"))
    maybeTotalWord.foreach(point => println(s"total found: $point"))
  })
}
