package io.scalac.auth

import io.scalac.domain.ServiceFailure

import scala.concurrent.Future

trait UserService {
  def authenticateUserAsync(name: String): Future[Either[ServiceFailure, Unit]]
  def authenticateUser(name: String): Either[ServiceFailure, Unit]
}

object UserService {
  final case class UnknownUser(message: String) extends ServiceFailure
}