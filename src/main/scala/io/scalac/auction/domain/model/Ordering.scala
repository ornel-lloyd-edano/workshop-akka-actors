package io.scalac.auction.domain.model

object Ordering {

  implicit val lotPriceOrdering = new Ordering[LotPrice] {
    override def compare(x: LotPrice, y: LotPrice): Int = {
      val auctionIdDiff = x.auctionId.compareTo(y.auctionId)
      lazy val lotIdDiff = x.lotId.compareTo(y.lotId)
      lazy val priceDiff = (x.price.map(_ * 1000)getOrElse(BigDecimal(0)) - y.price.map(_ * 1000).getOrElse(BigDecimal(0))).toInt
      if (auctionIdDiff == 0) {
        if (lotIdDiff == 0) {
          priceDiff
        } else {
          lotIdDiff
        }
      } else {
        auctionIdDiff
      }
    }
  }

  implicit def bidResultOrdering[T <: BidResult] = new Ordering[T] {
    override def compare(x: T, y: T): Int = {
      val userIdDiff = x.bid.userId.compareTo(y.bid.userId)
      lazy val auctionIdDiff = x.bid.auctionId.compareTo(y.bid.auctionId)
      lazy val lotIdDiff = x.bid.lotId.compareTo(y.bid.lotId)
      lazy val amountDiff = ((x.bid.amount * 1000) - (y.bid.amount * 1000)).toInt
      if (userIdDiff == 0) {
        if (auctionIdDiff == 0) {
          if (lotIdDiff == 0) {
            amountDiff
          } else lotIdDiff
        } else auctionIdDiff
      } else userIdDiff
    }
  }

}
