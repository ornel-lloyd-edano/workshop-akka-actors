package io.scalac.auction

import java.util.UUID

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import io.scalac.auction.AuctionActor._

object AuctionActorManager {

  sealed trait AuctionMgmtCommand
  sealed trait AuctionMgmtResponse

  final case class Create(replyTo: ActorRef[AuctionMgmtResponse]) extends AuctionMgmtCommand
  final case class Start(auctionId: String, replyTo: ActorRef[AuctionMgmtResponse]) extends AuctionMgmtCommand
  final case class Stop(auctionId: String, replyTo: ActorRef[AuctionMgmtResponse]) extends AuctionMgmtCommand
  final case class AddLot(auctionId: String, description: Option[String],
                          minBidAmount: Option[BigDecimal], replyTo: ActorRef[AuctionMgmtResponse]) extends AuctionMgmtCommand
  final case class RemoveLot(auctionId: String, lotId: String, replyTo: ActorRef[AuctionMgmtResponse]) extends AuctionMgmtCommand
  final case class RemoveAllLots(auctionId: String, replyTo: ActorRef[AuctionMgmtResponse]) extends AuctionMgmtCommand
  final case class GetLot(auctionId: String, lotId: String, replyTo: ActorRef[AuctionMgmtResponse]) extends AuctionMgmtCommand
  final case class GetAllLotsByAuction(auctionId: String, replyTo: ActorRef[AuctionMgmtResponse]) extends AuctionMgmtCommand
  final case class Bid(userId: String, auctionId: String, lotId: String, amount: BigDecimal, maxBidAmount: BigDecimal, replyTo: ActorRef[AuctionMgmtResponse]) extends AuctionMgmtCommand
  final case class WrappedAuctionActorResponse(response: AuctionActor.AuctionResponse) extends AuctionMgmtCommand


  final case class Created(auctionId: String) extends AuctionMgmtResponse
  final case class Started(auctionId: String) extends AuctionMgmtResponse
  final case class Stopped(auctionId: String) extends AuctionMgmtResponse
  final case class LotAdded(auctionId: String, lotId: String) extends AuctionMgmtResponse
  final case class LotRemoved(auctionId: String, lotId: String) extends AuctionMgmtResponse
  final case class AllLotsRemoved(auctionId: String, lotIds: Seq[String]) extends AuctionMgmtResponse
  final case class LotDetails(auctionId: String, lotId: String, description: Option[String],
                              currentTopBidder: Option[String], currentBidAmount: Option[BigDecimal]) extends AuctionMgmtResponse
  final case class AggregatedLotDetails(lotDetails: Seq[LotDetails]) extends  AuctionMgmtResponse
  final case class BidAccepted(userId: String, lotId: String) extends AuctionMgmtResponse
  final case class BidRejected(userId: String, lotId: String) extends AuctionMgmtResponse
  final case class AuctionNotFound(auctionId: String) extends AuctionMgmtResponse
  final case class LotNotFound(auctionId: String, lotId: String) extends AuctionMgmtResponse
  case object CommandRejected extends AuctionMgmtResponse

  private val singleton: Behavior[AuctionMgmtCommand] =
    Behaviors.withStash(100) { buffer=>
      Behaviors.setup(context => new AuctionActorManager(buffer, context).running(None))
    }
  def apply(): Behavior[AuctionMgmtCommand] = singleton

}

class AuctionActorManager private(buffer: StashBuffer[AuctionActorManager.AuctionMgmtCommand],
                                  context: ActorContext[AuctionActorManager.AuctionMgmtCommand]) {
  import AuctionActorManager._

  private var auctionActors = Map[String, ActorRef[AuctionCommand]]()
  private var idCounter: Int = 0
  val auctionActorMsgAdapter: ActorRef[AuctionActor.AuctionResponse] = context.messageAdapter(WrappedAuctionActorResponse(_))

   def running(replyTo: Option[ActorRef[AuctionMgmtResponse]]): Behavior[AuctionActorManager.AuctionMgmtCommand] =  Behaviors.receiveMessagePartial {
    case Create(replyTo)=>
      idCounter += 1
      val auctionId = idCounter.toString
      val auctionActor = context.spawn(AuctionActor(auctionId), s"AuctionActor-$auctionId")
      auctionActors += (auctionId -> auctionActor)
      replyTo ! Created(auctionId)
      running(Some(replyTo))

    case AddLot(auctionId, maybeDescription, maybeMinBidAmount, replyTo)=>
      auctionActors.get(auctionId) match {
        case Some(auctionActor)=>
          auctionActor ! AuctionActor.AddLot(maybeDescription, maybeMinBidAmount, auctionActorMsgAdapter)
        case None=>
          context.log.warn(s"Unable to add a lot to auction $auctionId because auction was not found")
          replyTo ! AuctionNotFound(auctionId)
      }
      running(Some(replyTo))

    case RemoveLot(auctionId, lotId, replyTo)=>
      auctionActors.get(auctionId) match {
        case Some(auctionActor) =>
          auctionActor ! AuctionActor.RemoveLot(lotId, auctionActorMsgAdapter)
        case None=>
          context.log.warn(s"Unable to remove a lot from auction $auctionId because auction was not found")
          replyTo ! AuctionNotFound(auctionId)
      }
      running(Some(replyTo))

    case RemoveAllLots(auctionId, replyTo)=>
      auctionActors.get(auctionId) match {
        case Some(auctionActor)=>
          auctionActor ! AuctionActor.RemoveAllLots(auctionActorMsgAdapter)
        case _=>
          context.log.warn(s"Unable to remove lots from auction $auctionId because auction was not found")
          replyTo ! AuctionNotFound(auctionId)
      }
      running(Some(replyTo))

    case Start(auctionId, replyTo)=>
      auctionActors.get(auctionId) match {
        case Some(auctionActor)=>
          auctionActor ! AuctionActor.Start(auctionActorMsgAdapter)
        case _=>
          context.log.warn(s"Unable to start auction $auctionId because it was not found")
          replyTo ! AuctionNotFound(auctionId)
      }
      running(Some(replyTo))

    case Bid(userId, auctionId, lotId, amount, maxBidAmount, replyTo)=>
      auctionActors.get(auctionId) match {
        case Some(auctionActor)=>
          auctionActor ! AuctionActor.Bid(userId, lotId, amount, maxBidAmount, auctionActorMsgAdapter)
        case _=>
          context.log.warn(s"Unable to bid lot at auction $auctionId because auction was not found")
          replyTo ! AuctionNotFound(auctionId)
      }
      running(Some(replyTo))

    case GetLot(auctionId, lotId, replyTo)=>
      auctionActors.get(auctionId) match {
        case Some(auctionActor)=>
          auctionActor ! AuctionActor.GetLot(lotId, auctionActorMsgAdapter)
        case _=>
          context.log.warn(s"Unable to get lot at auction $auctionId because auction was not found")
          replyTo ! AuctionNotFound(auctionId)
      }
      running(Some(replyTo))

    case GetAllLotsByAuction(auctionId, replyTo)=>
      auctionActors.get(auctionId) match {
        case Some(auctionActor)=>
          auctionActor ! AuctionActor.GetAllLots(auctionActorMsgAdapter)
        case _=>
          context.log.warn(s"Unable to get all lots at auction $auctionId because auction was not found")
          replyTo ! AuctionNotFound(auctionId)
      }
      running(Some(replyTo))

    case Stop(auctionId, replyTo)=>
      auctionActors.get(auctionId) match {
        case Some(auctionActor)=>
          auctionActor ! AuctionActor.Stop(auctionActorMsgAdapter)
        case _=>
          context.log.warn(s"Unable to stop auction $auctionId because it was not found")
          replyTo ! AuctionNotFound(auctionId)
      }
      running(Some(replyTo))

    case WrappedAuctionActorResponse(response)=>
      response match {
        case AuctionActor.LotCreated(auctionId, lotId)=>
          replyTo.foreach(_ ! LotAdded(auctionId, lotId))
        case AuctionActor.LotRemoved(auctionId, lotId)=>
          replyTo.foreach(_ ! LotRemoved(auctionId, lotId))
        case AuctionActor.LotsRemoved(auctionId, lotIds)=>
          replyTo.foreach(_ ! AllLotsRemoved(auctionId, lotIds))
        case AuctionActor.LotDetails(auctionId, lotId, description, currentTopBidder, currentBidAmount)=>
          replyTo.foreach(_ ! LotDetails(auctionId, lotId, description,
            currentTopBidder, currentBidAmount))
        case AuctionActor.AggregatedLotDetails(lotDetails)=>
          replyTo.foreach(_ ! AggregatedLotDetails(lotDetails.map(ld=>
            LotDetails(ld.auctionId, ld.lotId, ld.description, ld.currentTopBidder, ld.currentBidAmount))))
        case AuctionActor.Started(auctionId)=>
          replyTo.foreach(_ ! Started(auctionId))
        case AuctionActor.Stopped(auctionId)=>
          replyTo.foreach(_ ! Stopped(auctionId))
        case AuctionActor.BidAccepted(_, userId, lotId)=>
          replyTo.foreach(_ ! BidAccepted(userId, lotId))
        case AuctionActor.BidRejected(_, userId, lotId)=>
          replyTo.foreach(_ ! BidRejected(userId, lotId))
        case AuctionActor.LotNotFound(auctionId, lotId)=>
          replyTo.foreach(_ ! LotNotFound(auctionId, lotId))
        case AuctionActor.AuctionCommandRejected(auctionId)=>
          replyTo.foreach(_ ! CommandRejected)
      }
      Behaviors.same

  }

}
