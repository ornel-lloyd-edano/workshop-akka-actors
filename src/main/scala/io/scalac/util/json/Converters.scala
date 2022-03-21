package io.scalac.util.json

import play.api.libs.json.{JsValue => PlayJsValue, JsString=> PlayJsString, JsNumber=> PlayJsNumber,
  JsArray=> PlayJsArray, JsBoolean=> PlayJsBoolean, JsNull=> PlayJsNull, JsObject=> PlayJsObject}
import spray.json.{JsValue=> SprayJsValue, JsString=> SprayJsString, JsNumber=> SprayJsNumber,
  JsArray=> SprayJsArray, JsBoolean=> SprayJsBoolean, JsNull=> SprayJsNull, JsObject=> SprayJsObject}

object Converters {

  private def recursiveConvertToSpray(arg: PlayJsValue): SprayJsValue = arg match {
    case PlayJsString(value)=>
      SprayJsString(value)
    case PlayJsNumber(value)=>
      SprayJsNumber(value)
    case PlayJsBoolean(value)=>
      SprayJsBoolean(value)
    case PlayJsNull=>
      SprayJsNull

    case PlayJsArray(values)=>
      SprayJsArray(values.map(item=> recursiveConvertToSpray(item)):_*)

    case PlayJsObject(values)=>
      SprayJsObject( values.map(item=> item._1 -> recursiveConvertToSpray(item._2)).toMap )
  }

  private def recursiveConvertToPlay(arg: SprayJsValue): PlayJsValue = arg match {
    case SprayJsString(value)=>
      PlayJsString(value)
    case SprayJsNumber(value)=>
      PlayJsNumber(value)
    case SprayJsBoolean(value)=>
      PlayJsBoolean(value)
    case SprayJsNull=>
      PlayJsNull

    case SprayJsArray(values)=>
      PlayJsArray(values.map(item=> recursiveConvertToPlay(item)))

    case SprayJsObject(values)=>
      PlayJsObject( values.map(item=> item._1 -> recursiveConvertToPlay(item._2)) )
  }

  implicit class RichPlayJson(val arg: PlayJsValue) extends AnyVal {
    def toSprayJson: SprayJsValue = recursiveConvertToSpray(arg)
  }

  implicit class RichSprayJson(val arg: SprayJsValue) extends AnyVal {
    def toPlayJson: PlayJsValue = recursiveConvertToPlay(arg)
  }
}