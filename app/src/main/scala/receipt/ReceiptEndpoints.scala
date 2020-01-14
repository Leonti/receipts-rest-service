package receipt

import java.net.URLConnection

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.effect.Effect
import cats.implicits._
import cats.{Apply, Monad}
import fs2.text
import diffson.circe._
import diffson.jsonpatch._
import io.circe.Json
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.multipart.Multipart
import user.{UserId, UserIds}
import scala.util.Try

class ReceiptEndpoints[F[_]: Monad](
    receiptPrograms: ReceiptPrograms[F]
)(implicit F: Effect[F]) {

  object LastModifiedParamMatcher extends OptionalQueryParamDecoderMatcher[Long]("last-modified")
  object QueryParamMatcher        extends OptionalQueryParamDecoderMatcher[String]("q")

  private val service: AuthedRoutes[UserIds, F] = AuthedRoutes.of {

    case GET -> Root / "receipt" / receiptId as user =>
      receiptPrograms.findById(UserId(user.id), receiptId).map {
        case Some(receipt) => Response(status = Status.Ok).withEntity(receipt.asJson)
        case None          => Response(status = Status.NotFound).withEmptyBody: Response[F]
      }

    case GET -> Root / "receipt" :? LastModifiedParamMatcher(lastModified) +& QueryParamMatcher(query) as user =>
      receiptPrograms.findForUser(UserId(user.id), lastModified, query).map { receipts =>
        Response(status = Status.Ok).withEntity(receipts.asJson)
      }
  }

  private val getReceiptFile: AuthedRoutes[UserIds, F] = AuthedRoutes.of {
    case GET -> Root / "receipt" / receiptId / "file" / fileIdWithExt as user => {
      val fileId = fileIdWithExt.split('.')(0)

      receiptPrograms
        .receiptFileWithExtension(UserId(user.id), receiptId, fileId)
        .map {
          case Some(fileToServe) => {
            val mimeType = Option(URLConnection.guessContentTypeFromName("file." + fileToServe.ext)).getOrElse("application/octet-stream")
            Response(status = Status.Ok)
              .withBodyStream(fileToServe.source)
              .withHeaders(
                Header("Content-Type", mimeType)
              )
          }
          case None => Response(status = Status.NotFound).withEmptyBody: Response[F]
        }
    }
  }

  private val delete: AuthedRoutes[UserIds, F] = AuthedRoutes.of {
    case DELETE -> Root / "receipt" / receiptId as user =>
      receiptPrograms.removeReceipt(UserId(user.id), receiptId).map {
        case Some(_) => Response(status = Status.NoContent).withEmptyBody: Response[F]
        case None    => Response(status = Status.NotFound).withEmptyBody: Response[F]
      }
  }

  private val patch: AuthedRoutes[UserIds, F] = AuthedRoutes.of {
    case req @ PATCH -> Root / "receipt" / receiptId as user =>
      for {
        patch <- req.req.as[JsonPatch[Json]]
        resp <- receiptPrograms.patchReceipt(UserId(user.id), receiptId, patch).map {
          case Some(receipt) => Response(status = Status.Ok).withEntity(receipt.asJson): Response[F]
          case None          => Response(status = Status.NotFound).withEmptyBody: Response[F]
        }
      } yield resp
  }

  type ValidationResult[A] = ValidatedNel[String, A]

  private def toValidatedForm(m: Multipart[F]): F[ValidationResult[ReceiptForm[F]]] = {
    val validated: String => ValidatedNel[String, F[String]] = field =>
      m.parts
        .find(_.name.contains(field))
        .map(_.body.through(text.utf8Decode).compile.foldMonoid)
        .toValidNel(s"'$field' field is missing")

    val validatedReceipt: ValidatedNel[String, ReceiptField[F]] = m.parts
      .find(_.name.contains("receipt"))
      .flatMap(filePart => filePart.filename.map(filename => ReceiptField(filename, filePart.body)))
      .toValidNel("receipt field is missing or filename is not set")

    val transactionTimeF: F[Validated[NonEmptyList[String], Long]] = validated("transactionTime").sequence
      .map(_.andThen(t => Try(t.toLong).toOption.toValidNel("Field 'transactionTime' is not a number")))

    val totalF: F[Validated[NonEmptyList[String], Option[BigDecimal]]] = validated("total").sequence
      .map(_.map(total => Try(BigDecimal(total)).map(Some(_)).getOrElse(None)))

    val tagsF: F[Validated[NonEmptyList[String], List[String]]] = validated("tags").sequence
      .map(_.map(tags => if (tags.trim() == "") List() else tags.split(",").toList))

    val nested: F[F[ValidatedNel[String, ReceiptForm[F]]]] = for {
      transactionTimeV <- transactionTimeF
      totalV           <- totalF
      tagsV            <- tagsF
    } yield Apply[ValidatedNel[String, ?]]
      .map5(
        validatedReceipt,
        totalV,
        validated("description"),
        transactionTimeV,
        tagsV
      ) {
        case (receiptField, total, descriptionF, transactionTime, tags) =>
          descriptionF.map { description =>
            ReceiptForm(
              receiptField,
              total,
              description,
              transactionTime,
              tags
            )
          }
      }
      .sequence

    nested.flatMap(identity)
  }

  // TODO put proper error responses
  private val createReceipt: AuthedRoutes[UserIds, F] = AuthedRoutes.of {
    case req @ POST -> Root / "receipt" as user =>
      req.req.decode[Multipart[F]] { m =>
        for {
          receiptFormV <- toValidatedForm(m)
          resp <- receiptFormV match {
            case Valid(receiptForm) =>
              receiptPrograms.createReceipt(UserId(user.id), receiptForm).map {
                case Right(receipt) => Response(status = Status.Ok).withEntity(receipt.asJson): Response[F]
                case Left(_)        => Response(status = Status.BadRequest).withEmptyBody: Response[F]
              }
            case Invalid(_) => Monad[F].pure(Response(status = Status.BadRequest).withEmptyBody: Response[F])
          }
        } yield resp
      }
  }

  val authedRoutes: AuthedRoutes[UserIds, F] = createReceipt <+> getReceiptFile <+> service <+> delete <+> patch
}
