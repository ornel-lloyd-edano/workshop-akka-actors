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

}
