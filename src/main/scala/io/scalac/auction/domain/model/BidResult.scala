package io.scalac.auction.domain.model

sealed trait BidResult {
  val bid: SendBid
}

case class BidSuccess(bid: SendBid, lot: Lot) extends BidResult
case class BidFail(bid: SendBid, reason: String) extends BidResult
