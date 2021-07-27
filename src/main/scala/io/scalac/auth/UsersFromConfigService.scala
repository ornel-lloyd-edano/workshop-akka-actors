package io.scalac.auth
import io.scalac.domain.ServiceFailure
import io.scalac.util.ConfigProvider

import scala.concurrent.Future

class UsersFromConfigService(config: ConfigProvider) extends UserService {
  import UserService._
  val users = config.getStringArrayConfigVal("application.users").getOrElse(Seq.empty[String])

  override def authenticateUserAsync(name: String): Future[Either[ServiceFailure, Unit]] = {
    Future.successful(authenticateUser(name))
  }

  override def authenticateUser(name: String): Either[ServiceFailure, Unit] = {
    users.find(_ == name).map(_=> Right(()) ).getOrElse(Left(UnknownUser(s"User [$name] is not registered.")))
  }
}
