package io.scalac.auction.api.dto

case class SendBid(userId: String, lotId: String, auctionId: String, amount: BigDecimal, maxAmount: Option[BigDecimal])
