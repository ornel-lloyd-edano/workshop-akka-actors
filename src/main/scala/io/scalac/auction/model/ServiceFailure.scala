package io.scalac.auction.model

sealed trait ServiceFailure {
  val errorMsg: String
}

object ServiceFailure {
  final case class AuctionNotYetStarted(errorMsg: String) extends ServiceFailure
  final case class AuctionAlreadyStopped(errorMsg: String) extends ServiceFailure
  final case class AuctionNotFound(errorMsg: String) extends ServiceFailure
  final case class LotNotFound(errorMsg: String) extends ServiceFailure
}