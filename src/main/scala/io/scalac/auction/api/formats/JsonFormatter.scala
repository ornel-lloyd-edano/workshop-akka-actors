package io.scalac.auction.api.formats

import io.scalac.auction.api.dto.{Auction, Lot}
import spray.json.{DefaultJsonProtocol, JsValue, JsonWriter}

trait JsonFormatter extends DefaultJsonProtocol {

  implicit val auctionFormat = jsonFormat3(Auction)
  implicit val lotFormat = jsonFormat5(Lot)

}

object JsonFormatter extends JsonFormatter