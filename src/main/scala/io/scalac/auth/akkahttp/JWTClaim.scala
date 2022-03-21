package io.scalac.auth.akkahttp

import spray.json.JsValue

case class JWTClaim(key: String, value: JsValue)
