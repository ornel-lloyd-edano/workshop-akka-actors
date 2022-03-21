package io.scalac.auction.domain.model

case class LotId(id: String) extends AnyVal {
  override def toString: String = id
}
