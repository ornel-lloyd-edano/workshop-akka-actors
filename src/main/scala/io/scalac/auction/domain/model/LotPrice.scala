package io.scalac.auction.domain.model

case class LotPrice(lotId: String, auctionId: String, price: Option[BigDecimal])
