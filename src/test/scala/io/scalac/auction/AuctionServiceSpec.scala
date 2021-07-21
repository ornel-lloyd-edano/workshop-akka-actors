package io.scalac.auction

import io.scalac.auction.model.ServiceFailure.AuctionNotYetStarted
import io.scalac.auction.model.{Auction, AuctionId, AuctionStates, Lot, ServiceFailure}
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

class AuctionServiceSpec extends AsyncFlatSpec with Matchers with AsyncMockFactory  {
  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global


  "AuctionService" should "create an auction" in {
    val auctionService = mock[AuctionService]
    val results = Seq(auctionService.createAuction, auctionService.createAuction, auctionService.createAuction, auctionService.createAuction)
    val expected = Seq("1", "2", "3", "4").map(id=>Right(AuctionId(id)))
    Future.sequence(results) map { result=>
      result.sortBy {
        case Right(AuctionId(id))=> id
      } should be (expected)
    }
  }

  "AuctionService" should "get all available auctions" in {
    val auctionService = mock[AuctionService]
    val result = Future.sequence(Seq(auctionService.createAuction, auctionService.createAuction, auctionService.createAuction, auctionService.createAuction))
      .flatMap(_=> auctionService.getAuctions)

    val expected = Seq("1", "2", "3", "4").map(id=> Right(Auction(id, AuctionStates.Closed, Seq())))
    result map { result=>
      result.map(_.sortBy(_.id)) should be (expected)
    }
  }

  "AuctionService" should "add a lot in some of the available auctions" in {
    val auctionService = mock[AuctionService]
    val results =
      auctionService.createAuction.flatMap {
      case Right(_)=>
        Future.sequence(
          Seq(auctionService.addLot(auctionId = "1", description = Some("secret box1"), Some(BigDecimal(1000))),
            auctionService.addLot(auctionId = "2", description = Some("secret box2"), Some(BigDecimal(1000))),
            auctionService.addLot(auctionId = "3", description = Some("secret box3"), Some(BigDecimal(1000)))
        ))
    }

    val expected = Seq("1", "2", "3").map(id=> Right(Lot("1", id, Some(s"secret box$id"), None, None)))
    results map { result=>
      result.sortBy {
        case Right(lot)=> lot.auctionId + lot.id
      } should be (expected)
    }
  }

  "AuctionService" should "fail to bid if auction is not yet started" in {
    val auctionService = mock[AuctionService]
    val result = for {
      _ <- auctionService.createAuction
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box"), Some(BigDecimal(1000)))
      result <- auctionService.bid(auctionId = "1", lotId = "1", userId = "user1", amount = BigDecimal(2000), None)
    } yield result

    val expected = Left(AuctionNotYetStarted("User [user1] failed to bid on lot [1] because auction [1] is not yet started."))
    result map { result=>
      result should be (expected)
    }
  }

  "AuctionService" should "start an auction and able to bid a lot" in {
    val auctionService = mock[AuctionService]
    val result = for {
      _ <- auctionService.createAuction
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box"), Some(BigDecimal(1000)))
      _ <- auctionService.startAuction("1")
      result <- auctionService.bid(auctionId = "1", lotId = "1", userId = "user1", amount = BigDecimal(2000), None)
    } yield result

    val expected = Right(Lot(id = "1", auctionId = "1", description = Some("secret box"), topBidder = Some("user1"), topBid = Some(BigDecimal(2000))))
    result map { result=>
      result should be (expected)
    }
  }

  "AuctionService" should "get a lot by id" in {
    val auctionService = mock[AuctionService]
    val result = for {
      _ <- auctionService.createAuction
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box"), Some(BigDecimal(1000)))
      _ <- auctionService.startAuction("1")
      result <- auctionService.getLotById(auctionId = "1", lotId = "1")
    } yield result

    val expected = Right(Lot("1", "1", Some("secret box"), None, None))
    result map { result=>
      result should be (expected)
    }
  }

  "AuctionService" should "fail to get a lot in non-existing auction" in {
    val auctionService = mock[AuctionService]
    val result = for {
      _ <- auctionService.createAuction
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box"), Some(BigDecimal(1000)))
      _ <- auctionService.startAuction("1")
      result <- auctionService.getLotById(auctionId = "2", lotId = "1")
    } yield result

    val expected = Left(ServiceFailure.AuctionNotFound("Lot [1] was not found because auction [2] was not found."))
    result map { result=>
      result should be (expected)
    }
  }

  "AuctionService" should "fail to get non-existing lot in an existing auction" in {
    val auctionService = mock[AuctionService]
    val result = for {
      _ <- auctionService.createAuction
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box"), Some(BigDecimal(1000)))
      _ <- auctionService.startAuction("1")
      result <- auctionService.getLotById(auctionId = "1", lotId = "3")
    } yield result

    val expected = Left(ServiceFailure.LotNotFound("Lot [3] was not found in auction [1]."))
    result map { result=>
      result should be (expected)
    }
  }

  "AuctionService" should "get all lots in an auction" in {
    val auctionService = mock[AuctionService]
    val result = for {
      _ <- auctionService.createAuction
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box1"), Some(BigDecimal(1000)))
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box2"), Some(BigDecimal(1000)))
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box3"), Some(BigDecimal(1000)))
      _ <- auctionService.startAuction("1")
      result <- auctionService.getLotsByAuction(auctionId = "1")
    } yield result

    val expected = Right(Seq("1", "2", "3").map(id=> Lot(id, "1", Some(s"secret box$id"), None, None)))
    result map { result=>
      result.map(_.sortBy(_.id)) should be (expected)
    }
  }

  "AuctionService" should "end an auction and fail to add any more lot" in {
    val auctionService = mock[AuctionService]
    val result = for {
      _ <- auctionService.createAuction
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box1"), Some(BigDecimal(1000)))
      _ <- auctionService.endAuction("1")
      result <- auctionService.addLot(auctionId = "1", description = Some("secret box2"), Some(BigDecimal(1000)))
    } yield result

    val expected = Left(ServiceFailure.AuctionAlreadyStopped("Failed to add a new lot to auction [1] because it is already stopped."))
    result map {result=>
      result should be (expected)
    }
  }

  "AuctionService" should "end an auction and fail to bid" in {
    val auctionService = mock[AuctionService]
    val result = for {
      _ <- auctionService.createAuction
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box1"), Some(BigDecimal(1000)))
      _ <- auctionService.bid(auctionId = "1", lotId = "1", userId = "user1", amount = BigDecimal(2000), maxAmount = Some(BigDecimal(2500)))
      _ <- auctionService.endAuction("1")
      result <- auctionService.bid(auctionId = "1", lotId = "1", userId = "user2", amount = BigDecimal(3000), None)
    } yield result

    val expected = Left(ServiceFailure.AuctionAlreadyStopped("Failed to bid in auction [1] because it is already stopped."))
    result map {result=>
      result should be (expected)
    }
  }
}
