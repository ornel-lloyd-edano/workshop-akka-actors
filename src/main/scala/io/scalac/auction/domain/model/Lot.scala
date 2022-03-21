package io.scalac.auction.domain.model

case class Lot(id: String, auctionId: String, description: Option[String], topBidder: Option[String], topBid: Option[BigDecimal])
