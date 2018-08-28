package service
import java.io.File

import algebras.{FileAlg, RandomAlg}
import cats.Monad
import com.twitter.finagle.http.exp.Multipart.{FileUpload, InMemoryFileUpload, OnDiskFileUpload}
import model.ReceiptUpload
import cats.implicits._

import scala.language.higherKinds
import scala.util.Try

class FileUploadPrograms[F[_]: Monad](uploadsLocation: String, fileAlg: FileAlg[F], randomAlg: RandomAlg[F]) {
  import fileAlg._, randomAlg._

  def toReceiptUpload(fileUpload: FileUpload,
                      total: String,
                      description: String,
                      transactionTime: String,
                      tags: String): F[ReceiptUpload] = {

    for {
      randomGuid <- generateGuid()
      filePath = new File(new File(uploadsLocation), randomGuid)
      _ <- fileUpload match {
        case d: OnDiskFileUpload   => moveFile(d.content, filePath)
        case m: InMemoryFileUpload => bufToFile(m.content, filePath)
      }
    } yield
      ReceiptUpload(
        receipt = filePath,
        fileName = fileUpload.fileName,
        total = Try(BigDecimal(total)).map(Some(_)).getOrElse(None),
        description = description,
        transactionTime = transactionTime.toLong,
        tags = if (tags.trim() == "") List() else tags.split(",").toList
      )
  }
}
