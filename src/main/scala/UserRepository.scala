import model.User
import reactivemongo.api.ReadPreference
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONObjectID, BSONDocument}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class UserRepository extends MongoDao[User] {
  val collection: BSONCollection = db[BSONCollection]("users")

  def save(user: User): Future[User] = save(collection, user)

  def findUserById(userId: String): Future[Option[User]]  = {
    find(collection, BSONDocument("_id" -> userId))
  }

  def findUserByUserName(userName: String): Future[Option[User]] = {
    find(collection, BSONDocument("userName" -> userName))
  }

}