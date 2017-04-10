package repository

import model.User
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.BSONDocument

import scala.concurrent.Future

class UserRepository extends MongoDao[User] {
  lazy val collectionFuture: Future[BSONCollection] = dbFuture.map(db => db[BSONCollection]("users"))

  def save(user: User): Future[User] = save(collectionFuture, user)

  def findUserById(userId: String): Future[Option[User]] = {
    find(collectionFuture, BSONDocument("_id" -> userId))
  }

  def findUserByUserName(userName: String): Future[Option[User]] = {
    find(collectionFuture, BSONDocument("userName" -> userName))
  }

}
