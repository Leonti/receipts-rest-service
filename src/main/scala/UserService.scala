import de.choffmeister.auth.common.{Plain, PBKDF2, PasswordHasher}

import scala.concurrent.{Future, ExecutionContext}
import model.{CreateUserRequest, User}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Right

class UserService (userRepository: UserRepository) {

  implicit val executor: ExecutionContext = global

  private val hasher = new PasswordHasher(
    "pbkdf2", "hmac-sha1" :: "10000" :: "128" :: Nil,
    List(PBKDF2, Plain))

  def createUser(createUserRequest: CreateUserRequest): Future[Either[String, User]] = {
    val existingUser: Future[Option[User]] = findByUserName(createUserRequest.userName);
    val result: Future[Either[String, User]] = existingUser.flatMap(user =>
    user match {
      case Some(user) => Future(Left("User already exists"))
      case None => {
        val hashedUser = User(userName = createUserRequest.userName, passwordHash = hasher.hash(createUserRequest.password))
        userRepository.save(hashedUser).map(user => Right(user));
      }
    })
    result
  }

  def findById(id: String)(implicit ec: ExecutionContext): Future[Option[User]] =
    userRepository.findUserById(id)
  def findByUserName(userName: String)(implicit ec: ExecutionContext): Future[Option[User]] =
    userRepository.findUserByUserName(userName)
  def validatePassword(user: User, password: String)(implicit ec: ExecutionContext): Future[Boolean] =
    Future(hasher.validate(user.passwordHash, password))
}
