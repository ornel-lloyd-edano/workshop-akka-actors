package io.scalac.auction.domain.model

import io.scalac.domain

sealed trait ServiceFailure extends domain.ServiceFailure

object ServiceFailure {
  final case class AuctionNotReady(message: String) extends ServiceFailure
  final case class AuctionNotFound(message: String) extends ServiceFailure
  final case class LotNotFound(message: String) extends ServiceFailure
  final case class BidRejected(message: String) extends ServiceFailure

  final case class UnexpectedFailure(message: String) extends ServiceFailure
  final case class UnexpectedResponse(message: String) extends ServiceFailure
}