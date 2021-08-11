package io.scalac.auction.domain

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import io.scalac.auction.domain.actor.AuctionActorManager
import io.scalac.auction.domain.model.ServiceFailure.AuctionNotReady
import io.scalac.auction.domain.model._
import io.scalac.util.{Configs, ExecutionContexts}
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

class AuctionServiceSpec extends AsyncFlatSpec with BeforeAndAfterAll with Matchers with AsyncMockFactory  {

  val testKit = ActorTestKit()
  implicit val scheduler = testKit.scheduler
  implicit val ecProvider = ExecutionContexts
  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
  implicit val mat = Materializer(ActorSystem("test"))
  implicit val confProvider = Configs

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "AuctionService" should "create an auction" in {
    val actor = testKit.spawn(AuctionActorManager())
    val auctionService = new DefaultAuctionService(actor)
    val results = Seq(auctionService.createAuction, auctionService.createAuction, auctionService.createAuction, auctionService.createAuction)
    val expected = Seq("1", "2", "3", "4").map(id=>Right(AuctionId(id)))
    Future.sequence(results) map { result=>
      result.sortBy {
        case Right(AuctionId(id))=> id
      } should be (expected)
    }
  }

  "AuctionService" should "get all available auctions" in {
    val actor = testKit.spawn(AuctionActorManager())
    val auctionService = new DefaultAuctionService(actor)
    val result = Future.sequence(
      Seq(
        auctionService.createAuction,
        auctionService.createAuction,
        auctionService.createAuction,
        auctionService.createAuction)
    ).flatMap(_=> auctionService.getAuctions)

    val expected = Right(Seq("1", "2", "3", "4").map(id=> Auction(id, AuctionStates.Closed, Seq())))
    result map { result=>
      result should be (expected)
    }
  }

  "AuctionService" should "add a lot in some of the available auctions" in {
    val actor = testKit.spawn(AuctionActorManager())
    val auctionService = new DefaultAuctionService(actor)

    val results = for {
      _ <- auctionService.createAuction
      result1 <- auctionService.addLot(auctionId = "1", description = Some("secret box1"), Some(BigDecimal(1000)))
      result2 <- auctionService.addLot(auctionId = "1", description = Some("secret box2"), Some(BigDecimal(1000)))
      result3 <- auctionService.addLot(auctionId = "1", description = Some("secret box3"), Some(BigDecimal(1000)))
    } yield (Seq(result1, result2, result3))


    val expected = Seq("1", "2", "3").map(id=> Right(LotId(id)))
    results map { result=>
      result.sortBy {
        case Right(lot)=> lot.id
      } should be (expected)
    }
  }

  "AuctionService" should "fail to bid if auction is not yet started" in {
    val actor = testKit.spawn(AuctionActorManager())
    val auctionService = new DefaultAuctionService(actor)
    val result = for {
      _ <- auctionService.createAuction
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box"), Some(BigDecimal(1000)))
      result <- auctionService.bid(auctionId = "1", lotId = "1", userId = "user1", amount = BigDecimal(2000), None)
    } yield result

    val expected = Left(AuctionNotReady("User [user1] failed to bid because auction [1] is not yet started or was already closed."))
    result map { result=>
      result should be (expected)
    }
  }

  "AuctionService" should "start an auction and able to bid a lot" in {
    val actor = testKit.spawn(AuctionActorManager())
    val auctionService = new DefaultAuctionService(actor)
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
    val actor = testKit.spawn(AuctionActorManager())
    val auctionService = new DefaultAuctionService(actor)
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
    val actor = testKit.spawn(AuctionActorManager())
    val auctionService = new DefaultAuctionService(actor)
    val result = for {
      _ <- auctionService.createAuction
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box"), Some(BigDecimal(1000)))
      _ <- auctionService.startAuction("1")
      result <- auctionService.getLotById(auctionId = "2", lotId = "1")
    } yield result

    val expected = Left(ServiceFailure.AuctionNotFound("Get lot failed because auction [2] was not found."))
    result map { result=>
      result should be (expected)
    }
  }

  "AuctionService" should "fail to get non-existing lot in an existing auction" in {
    val actor = testKit.spawn(AuctionActorManager())
    val auctionService = new DefaultAuctionService(actor)
    val result = for {
      _ <- auctionService.createAuction
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box"), Some(BigDecimal(1000)))
      _ <- auctionService.startAuction("1")
      result <- auctionService.getLotById(auctionId = "1", lotId = "3")
    } yield result

    val expected = Left(ServiceFailure.LotNotFound("Get lot failed because lot [3] was not found."))
    result map { result=>
      result should be (expected)
    }
  }

  "AuctionService" should "get all lots in an auction" in {
    val actor = testKit.spawn(AuctionActorManager())
    val auctionService = new DefaultAuctionService(actor)
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
    val actor = testKit.spawn(AuctionActorManager())
    val auctionService = new DefaultAuctionService(actor)
    val result = for {
      _ <- auctionService.createAuction
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box1"), Some(BigDecimal(1000)))
      _ <- auctionService.startAuction("1")
      _ <- auctionService.endAuction("1")
      result <- auctionService.addLot(auctionId = "1", description = Some("secret box2"), Some(BigDecimal(1000)))
    } yield result

    val expected = Left(ServiceFailure.AuctionNotReady("Failed to add lot because auction [1] is not yet started or was already closed."))
    result map {result=>
      result should be (expected)
    }
  }

  "AuctionService" should "end an auction and fail to bid" in {
    val actor = testKit.spawn(AuctionActorManager())
    val auctionService = new DefaultAuctionService(actor)
    val result = for {
      _ <- auctionService.createAuction
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box1"), Some(BigDecimal(1000)))
      _ <- auctionService.bid(auctionId = "1", lotId = "1", userId = "user1", amount = BigDecimal(2000), maxAmount = Some(BigDecimal(2500)))
      _ <- auctionService.startAuction("1")
      _ <- auctionService.endAuction("1")
      result <- auctionService.bid(auctionId = "1", lotId = "1", userId = "user2", amount = BigDecimal(3000), None)
    } yield result

    val expected = Left(ServiceFailure.AuctionNotReady("User [user2] failed to bid because auction [1] is not yet started or was already closed."))
    result map {result=>
      result should be (expected)
    }
  }

  "AuctionService with AuctionStreamingService" should "stream lot prices" in {
    val actor = testKit.spawn(AuctionActorManager())
    val auctionService = new DefaultAuctionService(actor)
    val dataPreparation = for {
      _ <- auctionService.createAuction
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box1"), None)
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box2"), None)
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box3"), None)
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box4"), None)
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box5"), None)
      _ <- auctionService.startAuction("1")
      _ <- auctionService.bid(auctionId = "1", lotId = "1", userId = "user1", amount = BigDecimal(1000), None)
      _ <- auctionService.bid(auctionId = "1", lotId = "2", userId = "user2", amount = BigDecimal(2000), None)
      _ <- auctionService.bid(auctionId = "1", lotId = "3", userId = "user3", amount = BigDecimal(3000), None)
      _ <- auctionService.bid(auctionId = "1", lotId = "4", userId = "user4", amount = BigDecimal(4000), None)
      _ <- auctionService.bid(auctionId = "1", lotId = "5", userId = "user5", amount = BigDecimal(5000), None)
      result <- auctionService.getLotsByAuction(auctionId = "1")
    } yield result
    val expectedMockData = Seq(
      Lot("1", "1", Some("secret box1"), topBidder = Some("user1"), topBid = Some(BigDecimal(1000))),
      Lot("2", "1", Some("secret box2"), topBidder = Some("user2"), topBid = Some(BigDecimal(2000))),
      Lot("3", "1", Some("secret box3"), topBidder = Some("user3"), topBid = Some(BigDecimal(3000))),
      Lot("4", "1", Some("secret box4"), topBidder = Some("user4"), topBid = Some(BigDecimal(4000))),
      Lot("5", "1", Some("secret box5"), topBidder = Some("user5"), topBid = Some(BigDecimal(5000)))
    )

    //prepare GetLot(1, 1, 1000), GetLot(1, 2, 2000) ... GetLot(1, 5, 5000) x 10 = 50
    val source = Source( (1 to 50).map(i=> GetLotPrice(auctionId = Some("1"), lotId = Some( (if(i % 5 == 0) 5 else i % 5).toString))) )
    val flowToTest: Flow[GetLotPrice, Seq[LotPrice], NotUsed] = auctionService.lotPricesFlow
    val sink = Sink.fold[Seq[LotPrice], Seq[LotPrice]](Seq.empty[LotPrice]) {
      case (accumulatedLotPrices, currentLotPrices)=>
        (accumulatedLotPrices ++ currentLotPrices)
    }
    def streamResult = source.via(flowToTest).runWith(sink)

    //expect LotPrice(1, 1, 1000), LotPrice(2, 1, 2000) ... LotPrice(5, 1, 5000) x 10 = 50
    val expectedStreamResult = (1 to 50).map(i=> LotPrice( lotId = (if(i % 5 == 0) 5 else i % 5).toString, auctionId = "1",
      price = Some(BigDecimal( (if(i % 5 == 0) 5000 else (i % 5) * 1000 )  ))  ) )

    val results = for {
      result1 <- dataPreparation
      result2 <- streamResult
    } yield (result1, result2)

    import io.scalac.auction.domain.model.Ordering._
    results map { results=>

      val dataPrepared = results._1
      dataPrepared should be(Right(expectedMockData))

      val streamResult = results._2
      streamResult.sorted should be (expectedStreamResult.sorted)
    }
  }

  "AuctionService with AuctionStreamingService" should "stream bids" in {
    val actor = testKit.spawn(AuctionActorManager())
    val auctionService = new DefaultAuctionService(actor)
    val dataPreparation = for {
      _ <- auctionService.createAuction
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box1"), None)
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box2"), None)
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box3"), None)
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box4"), None)
      _ <- auctionService.addLot(auctionId = "1", description = Some("secret box5"), None)
      _ <- auctionService.startAuction("1")
      _ <- auctionService.bid(auctionId = "1", lotId = "1", userId = "user1", amount = BigDecimal(1000), None)
      _ <- auctionService.bid(auctionId = "1", lotId = "2", userId = "user2", amount = BigDecimal(2000), None)
      _ <- auctionService.bid(auctionId = "1", lotId = "3", userId = "user3", amount = BigDecimal(3000), None)
      _ <- auctionService.bid(auctionId = "1", lotId = "4", userId = "user4", amount = BigDecimal(4000), None)
      _ <- auctionService.bid(auctionId = "1", lotId = "5", userId = "user5", amount = BigDecimal(5000), None)
      result <- auctionService.getLotsByAuction(auctionId = "1")
    } yield result
    val expectedMockData = Seq(
      Lot("1", "1", Some("secret box1"), topBidder = Some("user1"), topBid = Some(BigDecimal(1000))),
      Lot("2", "1", Some("secret box2"), topBidder = Some("user2"), topBid = Some(BigDecimal(2000))),
      Lot("3", "1", Some("secret box3"), topBidder = Some("user3"), topBid = Some(BigDecimal(3000))),
      Lot("4", "1", Some("secret box4"), topBidder = Some("user4"), topBid = Some(BigDecimal(4000))),
      Lot("5", "1", Some("secret box5"), topBidder = Some("user5"), topBid = Some(BigDecimal(5000)))
    )

    //prepare SendBids, bid 1 to 5 will fail but 6 to 10 will succeed
    val source = Source( (1 to 10).map(i=> SendBid(userId = "ornel", auctionId = "1",
      lotId = (if(i % 5 == 0) 5 else i % 5).toString, amount = BigDecimal(1000 * i), maxAmount = None)) )
    val flowToTest: Flow[SendBid, BidResult, NotUsed] = auctionService.bidsFlow
    val sink = Sink.seq[BidResult]

    def streamResult = source.via(flowToTest).runWith(sink)
    val expectedStreamResult = (1 to 10).map(i=> {
      val sendBid = SendBid(userId = "ornel", auctionId = "1", lotId = (if(i % 5 == 0) 5 else i % 5).toString, amount = BigDecimal(1000 * i), maxAmount = None)
      if (i < 6) {
        BidFail(sendBid, s"User [ornel] failed to top the current top bidder for lot [$i] in auction [1].")
      } else {
        val idx = i - 5
        BidSuccess(sendBid, Lot(id = idx.toString, auctionId = "1", description = Some(s"secret box$idx"),
          topBidder = Some("ornel"), topBid = Some(BigDecimal(i * 1000)) ))
      }
    })

    val results = for {
      result1 <- dataPreparation
      result2 <- streamResult
    } yield (result1, result2)

    import io.scalac.auction.domain.model.Ordering._
    results map { results=>

      val dataPrepared = results._1
      dataPrepared should be(Right(expectedMockData))

      val streamResult = results._2
      streamResult.sorted should be (expectedStreamResult.sorted)
    }
  }
}
