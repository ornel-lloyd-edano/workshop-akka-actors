package io.scalac.auction.api.dto

case class UserBid(userId: String, amount: BigDecimal, maxBid: Option[BigDecimal])
