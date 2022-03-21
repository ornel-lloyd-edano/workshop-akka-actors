package io.scalac.auth.akkahttp

import spray.json.JsObject

case class JWTClaims(value: JsObject) extends AnyVal