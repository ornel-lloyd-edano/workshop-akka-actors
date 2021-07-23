package io.scalac.auction.domain.model

case class AuctionId(id: String) extends AnyVal {
  override def toString: String = id
}
