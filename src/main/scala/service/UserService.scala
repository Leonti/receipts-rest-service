package service

import java.util.concurrent.Executors

import de.choffmeister.auth.common.{PBKDF2, PasswordHasher, Plain}
import model.{CreateUserRequest, User}
import repository.UserRepository

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Right

class UserService(userRepository: UserRepository) {

  implicit val executor: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  private val hasher = new PasswordHasher("pbkdf2", "hmac-sha1" :: "10000" :: "128" :: Nil, List(PBKDF2, Plain))

  def createUser(createUserRequest: CreateUserRequest): Future[Either[String, User]] = {
    findByUserName(createUserRequest.userName).flatMap({
      case Some(user) => Future(Left("User already exists"))
      case None =>
        userRepository
          .save(
            User(
              userName = createUserRequest.userName,
              passwordHash = hasher.hash(createUserRequest.password)
            )
          )
          .map(user => Right(user))
    })
  }

  def createGoogleUser(email: String): Future[User] = {
    for {
      existingUser <- findByUserName(email)
      createdUser <- existingUser match {
        case Some(user) => Future.failed(new RuntimeException("User already exists"))
        case None => {
          val hashedUser = User(userName = email)
          userRepository.save(hashedUser)
        }
      }
    } yield createdUser
  }

  def findById(id: String): Future[Option[User]] =
    userRepository.findUserById(id)

  def findByUserName(userName: String): Future[Option[User]] =
    userRepository.findUserByUserName(userName)

  def findByUserNameWithPassword(userName: String, password: String): Future[Option[User]] =
    userRepository.findUserByUserName(userName).map(_.filter(user => hasher.validate(user.passwordHash, password)))
}
