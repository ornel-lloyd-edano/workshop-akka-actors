package io.scalac.auction.domain

import io.scalac.auction.domain.model.{Auction, AuctionId, Lot, LotId, ServiceFailure}

import scala.concurrent.Future

trait  AuctionService {

  def createAuction: Future[Either[ServiceFailure, AuctionId]]

  def getAuctions: Future[Either[ServiceFailure, Seq[Auction]]]

  def addLot(auctionId: String, description: Option[String], minBidAmount: Option[BigDecimal]): Future[Either[ServiceFailure, LotId]]

  def startAuction(auctionId: String): Future[Either[ServiceFailure, Unit]]

  def endAuction(auctionId: String): Future[Either[ServiceFailure, Unit]]

  def getLotById(auctionId: String, lotId: String): Future[Either[ServiceFailure, Lot]]

  def getLotsByAuction(auctionId: String): Future[Either[ServiceFailure, Seq[Lot]]]

  def bid(auctionId: String, lotId: String, userId: String, amount: BigDecimal, maxAmount: Option[BigDecimal]): Future[Either[ServiceFailure, Lot]]
}
