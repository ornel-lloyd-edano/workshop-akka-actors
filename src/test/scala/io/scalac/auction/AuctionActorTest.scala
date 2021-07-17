package io.scalac.auction

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AuctionActorTest extends AnyWordSpec with BeforeAndAfterAll with Matchers {
  val testKit = ActorTestKit()
  override def afterAll(): Unit = testKit.shutdownTestKit()

  val auctionId = UUID.randomUUID().toString
  val auctionActor = testKit.spawn(AuctionActor(auctionId))
  val probe = testKit.createTestProbe[AuctionActor.AuctionResponse]()

  "AuctionActor in the closed state" should {
    "accept AddLot message while in this state and reply with LotCreated" in {
      auctionActor ! AuctionActor.AddLot(Some("notebooks from Nikola Tesla's library"), Some(BigDecimal(1000)), probe.ref)
      auctionActor ! AuctionActor.AddLot(Some("old stuff from attic"), Some(BigDecimal(1000)), probe.ref)
      auctionActor ! AuctionActor.AddLot(Some("large fossilized eggs"), Some(BigDecimal(1000)), probe.ref)
      probe.expectMessage(AuctionActor.LotCreated(auctionId, "1"))
      probe.expectMessage(AuctionActor.LotCreated(auctionId, "2"))
      probe.expectMessage(AuctionActor.LotCreated(auctionId, "3"))
    }

    "accept RemoveAllLots message while in this state and reply with LotsRemoved" in {
      auctionActor ! AuctionActor.RemoveAllLots(probe.ref)
      probe.expectMessage(AuctionActor.LotsRemoved(auctionId, Seq("1", "2", "3")))
    }

    "accept RemoveLot message while in this state and reply with LotRemoved" in {
      auctionActor ! AuctionActor.AddLot(Some("notebooks from Nikola Tesla's library"), Some(BigDecimal(1000)), probe.ref)
      auctionActor ! AuctionActor.AddLot(Some("old stuff from attic"), Some(BigDecimal(1000)), probe.ref)
      auctionActor ! AuctionActor.AddLot(Some("large fossilized eggs"), Some(BigDecimal(1000)), probe.ref)
      auctionActor ! AuctionActor.AddLot(Some("pandora's box"), Some(BigDecimal(9999)), probe.ref)
      auctionActor ! AuctionActor.AddLot(Some("old pirate chest"), Some(BigDecimal(7777)), probe.ref)
      probe.expectMessage(AuctionActor.LotCreated(auctionId, "4"))
      probe.expectMessage(AuctionActor.LotCreated(auctionId, "5"))
      probe.expectMessage(AuctionActor.LotCreated(auctionId, "6"))
      probe.expectMessage(AuctionActor.LotCreated(auctionId, "7"))
      probe.expectMessage(AuctionActor.LotCreated(auctionId, "8"))
      auctionActor ! AuctionActor.RemoveLot("5", probe.ref)
      probe.expectMessage(AuctionActor.LotRemoved(auctionId, "5"))
    }

    "accept RemoveLot message and reply LotNotFound if lotId is not found" in {
      auctionActor ! AuctionActor.RemoveLot("5", probe.ref)
      probe.expectMessage(AuctionActor.LotNotFound(auctionId, "5"))
    }

    "accept Start and reply with Started then transition to in-progress state" in {
      auctionActor ! AuctionActor.Start(probe.ref)
      probe.expectMessage(AuctionActor.Started(auctionId))
    }
  }

  "AuctionActor in the in-progress state" should {
    val bidderUserId = UUID.randomUUID().toString
    "ignore AddLot message" in {
      auctionActor ! AuctionActor.AddLot(Some("rare fossilized insects"), Some(BigDecimal(1000)), probe.ref)
      probe.expectMessage(AuctionActor.AuctionCommandRejected(auctionId))
    }

    "ignore RemoveLot message" in {
      auctionActor ! AuctionActor.RemoveLot("6", probe.ref)
      probe.expectMessage(AuctionActor.AuctionCommandRejected(auctionId))
    }

    "ignore Start message" in {
      auctionActor ! AuctionActor.Start(probe.ref)
      probe.expectMessage(AuctionActor.AuctionCommandRejected(auctionId))
    }

    "accept GetLot and reply with LotDetails" in {
      auctionActor ! AuctionActor.GetLot("6", probe.ref)
      probe.expectMessage(AuctionActor.LotDetails(auctionId, "6", Some("large fossilized eggs"), None, None))
    }

    "accept GetLot and reply with LotNotFound if no matching lotId" in {
      auctionActor ! AuctionActor.GetLot("5", probe.ref)
      probe.expectMessage(AuctionActor.LotNotFound(auctionId, "5"))
    }

    "accept GetAllLots and reply with AggregatedLotDetails" in {
      auctionActor ! AuctionActor.GetAllLots(probe.ref)
      val results = Seq(
        AuctionActor.LotDetails(auctionId, "4", Some("notebooks from Nikola Tesla's library"), None, None),
        AuctionActor.LotDetails(auctionId, "6", Some("large fossilized eggs"), None, None),
        AuctionActor.LotDetails(auctionId, "7", Some("pandora's box"), None, None),
        AuctionActor.LotDetails(auctionId, "8", Some("old pirate chest"), None, None)
      )
      probe.expectMessage(AuctionActor.AggregatedLotDetails(results))
    }

    "stash Bid message while waiting in gatheringAllLotDetails state" in {
      auctionActor ! AuctionActor.GetAllLots(probe.ref)
      auctionActor ! AuctionActor.Bid(bidderUserId, "8", BigDecimal(1500), BigDecimal(2000), probe.ref)
      val results = Seq(
        AuctionActor.LotDetails(auctionId, "4", Some("notebooks from Nikola Tesla's library"), None, None),
        AuctionActor.LotDetails(auctionId, "6", Some("large fossilized eggs"), None, None),
        AuctionActor.LotDetails(auctionId, "7", Some("pandora's box"), None, None),
        AuctionActor.LotDetails(auctionId, "8", Some("old pirate chest"), None, None)
      )
      probe.expectMessage(AuctionActor.AggregatedLotDetails(results))
      probe.expectMessage(AuctionActor.BidAccepted(auctionId, bidderUserId, lotId = "8"))
    }

    "accept Bid and reply with BidAccepted if bidder outbids current top bid for that lot" in {
      auctionActor ! AuctionActor.Bid(bidderUserId, "6", BigDecimal(1500), BigDecimal(2000), probe.ref)
      probe.expectMessage(AuctionActor.BidAccepted(auctionId, bidderUserId, lotId = "6"))
    }

    "accept Bid and reply with BidRejected if bidder fails to outbid current top bid for that lot" in {
      auctionActor ! AuctionActor.Bid("anotherUserId", "6", BigDecimal(1500), BigDecimal(2000), probe.ref)
      probe.expectMessage(AuctionActor.BidRejected(auctionId, "anotherUserId", lotId = "6"))
    }

    "accept Bid and reply with LotNotFound if no matching lotId" in {
      auctionActor ! AuctionActor.Bid(bidderUserId, "3", BigDecimal(1500), BigDecimal(2000), probe.ref)
      probe.expectMessage(AuctionActor.LotNotFound(auctionId, "3"))
    }

    "accept Stop and reply with Stopped" in {
      auctionActor ! AuctionActor.Stop(probe.ref)
      probe.expectMessage(AuctionActor.Stopped(auctionId))
    }

    "ignore any other message when actor is in stopped state" in {
      auctionActor ! AuctionActor.Start(probe.ref)
      probe.expectMessage(AuctionActor.AuctionCommandRejected(auctionId))

      auctionActor ! AuctionActor.GetLot("7", probe.ref)
      probe.expectMessage(AuctionActor.AuctionCommandRejected(auctionId))

      auctionActor ! AuctionActor.Bid("anotherUserId", "7", BigDecimal(1500), BigDecimal(2000), probe.ref)
      probe.expectMessage(AuctionActor.AuctionCommandRejected(auctionId))
    }
  }
}
