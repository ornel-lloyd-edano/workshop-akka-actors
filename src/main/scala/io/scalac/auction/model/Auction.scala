package io.scalac.auction.model

case class Auction(id: String, status: AuctionStatus, lotIds: Seq[String])
