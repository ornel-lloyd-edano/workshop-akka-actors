package io.scalac.auction.api.dto

case class BidResult(userId: String, lotId: String, auctionId: String, amount: BigDecimal, result: String)
