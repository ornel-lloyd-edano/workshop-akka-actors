package io.scalac.domain.api.mapping

import io.scalac.auction.domain.model
import io.scalac.auction.api.dto

object Implicits {

  implicit class RichAuction(val auction: model.Auction) extends AnyVal {
    def toApi = dto.Auction(id = auction.id, status = auction.status.toString, lotIds = auction.lotIds)
  }

  implicit class RichLot(val lot: model.Lot) extends AnyVal {
    def toApi = dto.Lot(id = lot.id, auctionId = lot.auctionId,
      description = lot.description, topBidder = lot.topBidder, topBid = lot.topBid)
  }
}
