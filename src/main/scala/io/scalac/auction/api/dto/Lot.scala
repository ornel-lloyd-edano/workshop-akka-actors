package io.scalac.auction.api.dto

case class Lot(id: String, auctionId: String, description: Option[String], topBidder: Option[String], topBid: Option[BigDecimal])
