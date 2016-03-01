package model

import scala.util.control.NonFatal

import reactivemongo.bson._

object Serialization {

  def deserialize[T](doc: BSONDocument, value: =>T): T = try {

    value

  } catch {

    case NonFatal(e) =>

      import scala.compat.Platform.EOL

      println("Failed to deserialize document " + EOL + BSONDocument.pretty(doc) + " " + e.getStackTrace.mkString("", EOL, EOL))

      throw e

  }

}
