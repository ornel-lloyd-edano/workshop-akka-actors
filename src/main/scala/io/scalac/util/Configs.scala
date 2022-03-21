package io.scalac.util

import com.typesafe.config.ConfigFactory

import scala.util.Try

object Configs extends ConfigProvider {
  private val config = ConfigFactory.load()

  override def getIntConfigVal(path: String): Option[Int] = Try(config.getInt(path)).toOption
  override def getStringConfigVal(path: String): Option[String] = Try(config.getString(path)).toOption
}
