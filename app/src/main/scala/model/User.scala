package model

import reactivemongo.bson.{BSONDocumentWriter, BSONDocument, BSONDocumentReader}

case class User(
    id: String = java.util.UUID.randomUUID.toString,
    userName: String,
    externalIds: List[String]
) extends WithId

case class UserInfo(id: String, userName: String)

object UserInfo {
  def apply(user: User) = new UserInfo(id = user.id, userName = user.userName)
}

object User {

  implicit object ReceiptEntityBSONReader extends BSONDocumentReader[User] {

    def read(doc: BSONDocument): User =
      Serialization.deserialize(
        doc,
        User(
          id = doc.getAs[String]("_id").get,
          userName = doc.getAs[String]("userName").get,
          externalIds = doc.getAs[List[String]]("externalIds").get,
        )
      )
  }

  implicit object ReceiptEntityBSONWriter extends BSONDocumentWriter[User] {

    def write(user: User): BSONDocument = {
      BSONDocument(
        "_id"      -> user.id,
        "userName" -> user.userName,
        "externalIds" -> user.externalIds
      )
    }
  }

}
