package io.scalac.util.http

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import spray.json._

trait PayloadConverter {

  implicit class RichPayload[T](payload: T) {
    def toJsonHttpEntity(implicit jsonFormat: JsonFormat[T]): HttpEntity.Strict = {
      HttpEntity(ContentTypes.`application/json`, jsonFormat.write(payload).prettyPrint)
    }
  }

}

object PayloadConverter extends PayloadConverter
