package io.scalac.auction.domain.model

case class SendBid(userId: String, lotId: String, auctionId: String, amount: BigDecimal, maxAmount: Option[BigDecimal])
