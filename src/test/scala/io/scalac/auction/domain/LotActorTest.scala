package io.scalac.auction.domain

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LotActorTest extends AnyWordSpec with BeforeAndAfterAll with Matchers {

  val testKit = ActorTestKit()

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "LotActor" should {
    val lotId = UUID.randomUUID().toString
    val desc = Some("treasure from yamashita site")
    val minBid = Some(BigDecimal(100))
    val maxBid = Some(BigDecimal(999))
    val lotActor = testKit.spawn(LotActor(lotId, desc, minBid, maxBid), "TestLotActor-1")
    val userIdOfBestBidder = UUID.randomUUID().toString
    val bestBidderBidAmount = BigDecimal(2100)

    val probe = testKit.createTestProbe[LotActor.LotResponse]()

    "accept GetDetails and reply with LotDetails" in {
      lotActor ! LotActor.GetDetails(probe.ref)
      probe.expectMessage(LotActor.LotDetails(lotId, desc, None, None))
    }

    "accept Bid and reply with BidAccepted if bidder is the first to bid" in {
      val newUserId = UUID.randomUUID().toString
      val bidAmount = BigDecimal(1000)
      val maxBidAmount = BigDecimal(2000)
      lotActor ! LotActor.Bid(newUserId, bidAmount, maxBidAmount, probe.ref)
      probe.expectMessage(LotActor.BidAccepted(newUserId, lotId, bidAmount))
    }

    "reject Bid and reply with BidRejected if next bidder's bid amount fail to top the last bidder's max bid amount" in {
      val newUserId = UUID.randomUUID().toString
      val bidAmount = BigDecimal(1500)
      val maxBidAmount = BigDecimal(2500)
      lotActor ! LotActor.Bid(newUserId, bidAmount, maxBidAmount, probe.ref)
      probe.expectMessage(LotActor.BidRejected(newUserId, lotId, BigDecimal(1000)))
    }

    "accept Bid and reply with BidAccepted if next bidder's bid amount tops the last bidder's max bid amount" in {
      val maxBidAmount = BigDecimal(2500)
      lotActor ! LotActor.Bid(userIdOfBestBidder, bestBidderBidAmount, maxBidAmount, probe.ref)
      probe.expectMessage(LotActor.BidAccepted(userIdOfBestBidder, lotId, bestBidderBidAmount))
    }

    "accept GetDetails and reply with LotDetails including the current best bid" in {
      lotActor ! LotActor.GetDetails(probe.ref)
      probe.expectMessage(LotActor.LotDetails(lotId, desc, Some(userIdOfBestBidder), Some(bestBidderBidAmount)))
    }
  }

}
