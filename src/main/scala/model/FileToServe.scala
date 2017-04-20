package model

import akka.stream.IOResult
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Future

case class FileToServe(source: Source[ByteString, Future[IOResult]], ext: String)
