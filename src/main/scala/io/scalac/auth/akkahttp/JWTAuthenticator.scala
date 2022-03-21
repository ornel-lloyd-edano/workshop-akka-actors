package io.scalac.auth.akkahttp

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import com.github._3tty0n.jwt.{JWTResult, _}
import io.scalac.auth.akkahttp.Token.RawToken
import io.scalac.util.json.Converters._

import scala.util.Try

trait JWTAuthenticator extends TokenAuthenticator {
  def getSecret: Option[String]

  def extractJWT(claimValidator: JWTClaims=> Try[Unit]): Directive1[JWTClaims] = extractToken(None).flatMap {
    case Some(RawToken(value))=>
      JWT.decode(value, getSecret) match {
        case JWTResult.JWT(_, payload: play.api.libs.json.JsObject)=>

          val claims = JWTClaims(payload.toSprayJson.asJsObject)
          claimValidator(claims)
            .fold(err=> complete(StatusCodes.Unauthorized, err.getMessage), _=> provide(claims))

        case _=>
          complete(StatusCodes.Unauthorized, "Invalid json web token.")
      }
    case _=> complete(StatusCodes.Unauthorized, "Token missing in the request.")
  }

}
