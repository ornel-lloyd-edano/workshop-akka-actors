package io.scalac.auction.domain.actor.persistent

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import io.scalac.auction.domain.actor.persistent.AuctionActorManager.{AggregatedAuctionDetails, AuctionDetail}
import io.scalac.auction.domain.model.AuctionStates
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class AuctionActorManagerTest extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
    """) with
  AnyWordSpecLike with BeforeAndAfterAll with Matchers {

  override def afterAll(): Unit = testKit.shutdownTestKit()

  val auctionMgrActor = testKit.spawn(AuctionActorManager(UUID.randomUUID().toString))
  val probe = testKit.createTestProbe[AuctionActorManager.AuctionMgmtResponse]()

  "AuctionActorManager" should {
    "accept Create and reply with Created" in {
      auctionMgrActor ! AuctionActorManager.Create(probe.ref)
      probe.expectMessage(AuctionActorManager.Created("1"))
      auctionMgrActor ! AuctionActorManager.Create(probe.ref)
      probe.expectMessage(AuctionActorManager.Created("2"))
      auctionMgrActor ! AuctionActorManager.Create(probe.ref)
      probe.expectMessage(AuctionActorManager.Created("3"))
    }

    "accept AddLot and reply with LotAdded" in {
      auctionMgrActor ! AuctionActorManager.AddLot("1", None, None, probe.ref)
      auctionMgrActor ! AuctionActorManager.AddLot("1", None, None, probe.ref)
      auctionMgrActor ! AuctionActorManager.AddLot("1", None, None, probe.ref)
      auctionMgrActor ! AuctionActorManager.AddLot("1", None, None, probe.ref)
      probe.expectMessage(AuctionActorManager.LotAdded("1", "1"))
      probe.expectMessage(AuctionActorManager.LotAdded("1", "2"))
      probe.expectMessage(AuctionActorManager.LotAdded("1", "3"))
      probe.expectMessage(AuctionActorManager.LotAdded("1", "4"))
    }

    "accept AddLot and reply with AuctionNotFound" in {
      auctionMgrActor ! AuctionActorManager.AddLot("9", None, None, probe.ref)
      probe.expectMessage(AuctionActorManager.AuctionNotFound("9"))
    }

    "accept RemoveLot and reply with LotRemoved" in {
      auctionMgrActor ! AuctionActorManager.RemoveLot("1", "1", probe.ref)
      probe.expectMessage(AuctionActorManager.LotRemoved("1", "1"))
    }

    "accept RemoveLot and reply with LotNotFound" in {
      auctionMgrActor ! AuctionActorManager.RemoveLot("1", "1", probe.ref)
      probe.expectMessage(AuctionActorManager.LotNotFound("1", "1"))
    }

    "accept RemoveAllLot and reply with LotRemoved" in {
      auctionMgrActor ! AuctionActorManager.RemoveAllLots("1", probe.ref)
      probe.expectMessage(AuctionActorManager.AllLotsRemoved("1", Seq("2", "3", "4")))
    }

    "accept RemoveAllLot and reply with AuctionNotFound" in {
      auctionMgrActor ! AuctionActorManager.RemoveAllLots("9", probe.ref)
      probe.expectMessage(AuctionActorManager.AuctionNotFound("9"))
    }

    "accept Bid and GetLot but reply with CommandRejected because auction is not yet started" in {
      auctionMgrActor ! AuctionActorManager.AddLot("1", None, None, probe.ref)
      probe.expectMessage(AuctionActorManager.LotAdded("1", "5"))
      auctionMgrActor ! AuctionActorManager.Bid("new user", "1", "5", BigDecimal(3000), BigDecimal(4000), probe.ref)
      probe.expectMessage(AuctionActorManager.CommandRejected)
      auctionMgrActor ! AuctionActorManager.GetLot("1", "5", probe.ref)
      probe.expectMessage(AuctionActorManager.CommandRejected)
    }

    "accept Start and reply with Started" in {
      auctionMgrActor ! AuctionActorManager.Start("1", probe.ref)
      probe.expectMessage(AuctionActorManager.Started("1"))
    }

    "accept GetAllAuctions and reply with AggregatedAuctionDetails" in {
      auctionMgrActor ! AuctionActorManager.GetAllAuctions(probe.ref)
      val expected = AggregatedAuctionDetails(Seq(
        AuctionDetail("1", AuctionStates.Started),
        AuctionDetail("2", AuctionStates.Closed),
        AuctionDetail("3", AuctionStates.Closed)))
      probe.expectMessage(expected)
    }

    "able to accept Bid after auction is started" in {
      auctionMgrActor ! AuctionActorManager.Bid("new user1", "1", "5", BigDecimal(3000), BigDecimal(4000), probe.ref)
      probe.expectMessage(AuctionActorManager.BidAccepted("new user1", "5", "1", BigDecimal(3000)))
    }

    "able to accept GetLot after auction is started" in {
      auctionMgrActor ! AuctionActorManager.GetLot("1", "5", probe.ref)
      probe.expectMessage(AuctionActorManager.LotDetails("1", "5", None, Some("new user1"), Some(BigDecimal(3000))))
    }

    "accept Stop and reply with Stopped" in {
      auctionMgrActor ! AuctionActorManager.Stop("1", probe.ref)
      probe.expectMessage(AuctionActorManager.Stopped("1"))
    }

    "reject any message when actor is already stopped" in {
      auctionMgrActor ! AuctionActorManager.Start("1", probe.ref)
      probe.expectMessage(AuctionActorManager.CommandRejected)

      auctionMgrActor ! AuctionActorManager.AddLot("1", None, None, probe.ref)
      probe.expectMessage(AuctionActorManager.CommandRejected)

      auctionMgrActor ! AuctionActorManager.RemoveLot("1", "5", probe.ref)
      probe.expectMessage(AuctionActorManager.CommandRejected)
    }
  }
}
