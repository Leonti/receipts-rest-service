package repository

import model.User
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.BSONDocument

import scala.concurrent.Future

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