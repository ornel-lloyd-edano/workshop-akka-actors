package io.scalac.auction.domain.api.mapping

import io.scalac.auction.api.dto
import io.scalac.auction.domain.model

object Implicits {

  implicit class RichAuction(val auction: model.Auction) extends AnyVal {
    def toApi = dto.Auction(id = auction.id, status = auction.status.toString, lotIds = auction.lotIds)
  }

  implicit class RichLot(val lot: model.Lot) extends AnyVal {
    def toApi = dto.Lot(id = lot.id, auctionId = lot.auctionId,
      description = lot.description, topBidder = lot.topBidder, topBid = lot.topBid)
  }

  implicit class RichLotPrice(val lotPrice: model.LotPrice) extends AnyVal {
    def toApi = dto.LotPrice(lotId = lotPrice.lotId, auctionId = lotPrice.auctionId, price = lotPrice.price)
  }

  implicit class RichBidResult(val bidResult: model.BidResult) extends AnyVal {
    def toApi = bidResult match {
      case result: model.BidSuccess=>
        dto.BidResult(userId = result.bid.userId, lotId = result.bid.lotId, auctionId = result.bid.auctionId,
          amount = result.bid.amount, result = "Success" )
      case result: model.BidFail=>
        dto.BidResult(userId = result.bid.userId, lotId = result.bid.lotId, auctionId = result.bid.auctionId,
          amount = result.bid.amount, result = "Fail" )
    }
  }
}
