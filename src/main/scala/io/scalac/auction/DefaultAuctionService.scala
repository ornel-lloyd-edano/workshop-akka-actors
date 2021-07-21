package io.scalac.auction
import io.scalac.auction.model.{Auction, AuctionId, Lot, ServiceFailure}

import scala.concurrent.Future

class DefaultAuctionService() extends AuctionService {
  override def createAuction: Future[Either[ServiceFailure, AuctionId]] = ???

  override def getAuctions: Future[Either[ServiceFailure, Seq[Auction]]] = ???

  override def addLot(auctionId: String, description: Option[String], minBidAmount: Option[BigDecimal]): Future[Either[ServiceFailure, Lot]] = ???

  override def startAuction(auctionId: String): Future[Either[ServiceFailure, Auction]] = ???

  override def endAuction(auctionId: String): Future[Either[ServiceFailure, Auction]] = ???

  override def getLotById(auctionId: String, lotId: String): Future[Either[ServiceFailure, Lot]] = ???

  override def getLotsByAuction(auctionId: String): Future[Either[ServiceFailure, Seq[Lot]]] = ???

  override def bid(auctionId: String, lotId: String, userId: String, amount: BigDecimal, maxAmount: Option[BigDecimal]): Future[Either[ServiceFailure, Lot]] = ???
}
