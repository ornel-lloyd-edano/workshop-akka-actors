package io.scalac.auth.akkahttp

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.{complete, extractCredentials, provide}

trait TokenAuthenticator {

  def extractToken(isTokenValid: Option[String=> Boolean]): Directive1[Option[Token]] = {
    extractCredentials.flatMap {
      case Some(OAuth2BearerToken(token))=>
        isTokenValid match {
          case Some(validator)=>
            if (validator(token)) {
              provide(Some(Token.ValidatedToken(token)))
            } else {
              complete(StatusCodes.Unauthorized, "Invalid token.")
            }
          case None=>
            provide(Some(Token.RawToken(token)))
        }

      case _=> complete(StatusCodes.Unauthorized, "Token missing in the request.")
    }
  }

}
