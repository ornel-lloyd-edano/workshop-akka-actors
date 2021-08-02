package io.scalac.auction.api.domain.mapping

import io.scalac.auction.api.dto
import io.scalac.auction.domain.model

object Implicits {

  implicit class RichGetLotPrice(val arg: dto.GetLotPrice) extends AnyVal {
    def toDomain = model.GetLotPrice(auctionId = Some(arg.auctionId), lotId = Some(arg.lotId))
  }

  implicit class RichSendBid(val arg: dto.SendBid) extends AnyVal {
    def toDomain = model.SendBid(userId = arg.userId, lotId = arg.lotId, auctionId = arg.auctionId, amount = arg.amount, maxAmount = arg.maxAmount)
  }

}
