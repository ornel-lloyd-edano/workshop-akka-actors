package io.scalac.auction.api.formats

import io.scalac.auction.api.dto.{AddLot, Auction, Lot, UserBid}
import spray.json.DefaultJsonProtocol

trait JsonFormatter extends DefaultJsonProtocol {

  implicit val auctionFormat = jsonFormat3(Auction)
  implicit val lotFormat = jsonFormat5(Lot)
  implicit val bidFormat = jsonFormat3(UserBid)
  implicit val addLotFormat = jsonFormat2(AddLot)
}

object JsonFormatter extends JsonFormatter