package io.scalac.auction

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import io.scalac.auction
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AuctionActorManagerTest extends AnyWordSpec with BeforeAndAfterAll with Matchers {
  val testKit = ActorTestKit()
  override def afterAll(): Unit = testKit.shutdownTestKit()

  val auctionMgrActor = testKit.spawn(AuctionActorManager())
  val probe = testKit.createTestProbe[auction.AuctionActorManager.AuctionMgmtResponse]()

  "AuctionActorManager" should {
    "accept Create and reply with Created" in {
      auctionMgrActor ! AuctionActorManager.Create(probe.ref)
      probe.expectMessage(AuctionActorManager.Created("1"))
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

    "able to accept Bid after auction is started" in {
      auctionMgrActor ! AuctionActorManager.Bid("new user1", "1", "5", BigDecimal(3000), BigDecimal(4000), probe.ref)
      probe.expectMessage(AuctionActorManager.BidAccepted("new user1", "5"))
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
