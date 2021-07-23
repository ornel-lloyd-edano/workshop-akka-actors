package io.scalac.auction.api.dto

case class Auction(id: String, status: String, lotIds: Seq[String])
