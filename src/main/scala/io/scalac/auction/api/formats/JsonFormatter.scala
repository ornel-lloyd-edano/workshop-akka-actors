package io.scalac.auction.api.formats

import io.scalac.auction.api.dto.{AddLot, Auction, GetLotPrice, Lot, LotPrice, SendBid, UserBid}
import spray.json.DefaultJsonProtocol

trait JsonFormatter extends DefaultJsonProtocol {

  implicit val auctionFormat = jsonFormat3(Auction)
  implicit val lotFormat = jsonFormat5(Lot)
  implicit val bidFormat = jsonFormat3(UserBid)
  implicit val addLotFormat = jsonFormat2(AddLot)
  implicit val getLotPriceFormat = jsonFormat2(GetLotPrice)
  implicit val lotPriceFormat = jsonFormat3(LotPrice)
}

object JsonFormatter extends JsonFormatter