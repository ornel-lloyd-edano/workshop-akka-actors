package io.scalac.auction.api.auth

import akka.http.scaladsl.server.Route
import io.scalac.auth.UserService
import io.scalac.auth.akkahttp.{JWTAuthenticator, JWTClaim, JWTClaims}
import spray.json.JsString

import scala.util.{Failure, Success, Try}

trait AuctionApiJWTClaimValidator extends JWTAuthenticator {
  val userService: UserService

  private def getCustomClaim(key: String, claims: JWTClaims): Option[JWTClaim] = {
    claims.value.getFields(key).headOption.map(JWTClaim(key, _))
  }

  def validateUserClaim(claims: JWTClaims): Try[Unit] =
    getCustomClaim("user", claims).flatMap {
      case JWTClaim(_, JsString(user))=>
        userService.authenticateUser(user).toOption
    }.map(_=> Success(()))
      .getOrElse(Failure(new Exception(s"Invalid token.")))

  def authenticatedRoutes: Route
}
