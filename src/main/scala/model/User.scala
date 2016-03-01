package model

import reactivemongo.bson.{BSONDocumentWriter, BSONDocument, BSONDocumentReader}

case class User(id: String = java.util.UUID.randomUUID.toString, userName: String, passwordHash: String) extends WithId

case class CreateUserRequest(userName: String, password: String)

case class UserInfo(id: String, userName: String)

object UserInfo {
  def apply(user: User) = new UserInfo(id = user.id, userName = user.userName)
}

object User {

  implicit object ReceiptEntityBSONReader extends BSONDocumentReader[User] {

    def read(doc: BSONDocument): User = Serialization.deserialize(doc, User(
      id = doc.getAs[String]("_id").get,
      userName = doc.getAs[String]("userName").get,
      passwordHash = doc.getAs[String]("passwordHash").get
    ))
  }

  implicit object ReceiptEntityBSONWriter extends BSONDocumentWriter[User] {

    def write(user: User): BSONDocument = {
      BSONDocument(
        "_id" -> user.id,
        "userName" -> user.userName,
        "passwordHash" -> user.passwordHash
      )
    }
  }

}
