package io.scalac.auction.model

sealed trait ServiceFailure {
  val message: String
}

object ServiceFailure {
  final case class AuctionNotReady(message: String) extends ServiceFailure
  final case class AuctionNotFound(message: String) extends ServiceFailure
  final case class LotNotFound(message: String) extends ServiceFailure
  final case class BidRejected(message: String) extends ServiceFailure

  final case class UnexpectedFailure(message: String) extends ServiceFailure
  final case class UnexpectedResponse(message: String) extends ServiceFailure
}