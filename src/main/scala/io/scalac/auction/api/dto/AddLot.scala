package io.scalac.auction.api.dto

case class AddLot(description: Option[String], minBidAmount: Option[BigDecimal])
