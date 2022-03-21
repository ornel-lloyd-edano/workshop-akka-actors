package io.scalac.auth.akkahttp

sealed trait Token  {
  val value: String
}

object Token {
  case class ValidatedToken(value: String) extends Token
  case class RawToken(value: String)  extends Token
}