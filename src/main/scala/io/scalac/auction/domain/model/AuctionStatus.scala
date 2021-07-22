package io.scalac.auction.domain.model

sealed trait AuctionStatus {

}

object AuctionStates {
  case object Closed extends AuctionStatus
  case object Started extends AuctionStatus
  case object Stopped extends AuctionStatus
}
