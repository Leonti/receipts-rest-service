package model
import fs2.Stream

case class FileToServe[F[_]](source: Stream[F, Byte], ext: String)
