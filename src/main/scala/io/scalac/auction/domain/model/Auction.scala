package io.scalac.auction.domain.model

case class Auction(id: String, status: AuctionStatus, lotIds: Seq[String])
