package io.scalac.auction.api.dto

case class LotPrice(lotId: String, auctionId: String, price: Option[BigDecimal])
