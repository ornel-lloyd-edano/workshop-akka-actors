package io.scalac.auction

import akka.actor.typed.scaladsl.StashBuffer
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import io.scalac.auction.AuctionActor.AuctionCommand

object AuctionActor {

  sealed trait AuctionCommand
  sealed trait InProgressStateCommand {
    val replyTo: ActorRef[AuctionResponse]
  }
  sealed trait ClosedStateCommand {
    val replyTo: ActorRef[AuctionResponse]
  }

  sealed trait AuctionResponse

  final case class Start(replyTo: ActorRef[AuctionResponse]) extends AuctionCommand with ClosedStateCommand
  final case class Stop(replyTo: ActorRef[AuctionResponse]) extends AuctionCommand with InProgressStateCommand
  final case class AddLot(description: Option[String], minBidAmount: Option[BigDecimal],
                          replyTo: ActorRef[AuctionResponse]) extends AuctionCommand with ClosedStateCommand
  final case class RemoveLot(lotId: String, replyTo: ActorRef[AuctionResponse]) extends AuctionCommand with ClosedStateCommand
  final case class RemoveAllLots(replyTo: ActorRef[AuctionResponse]) extends AuctionCommand with ClosedStateCommand
  final case class GetLot(lotId: String, replyTo: ActorRef[AuctionResponse]) extends AuctionCommand with InProgressStateCommand
  final case class GetAllLots(replyTo: ActorRef[AuctionResponse]) extends AuctionCommand with InProgressStateCommand

  final case class Bid(userId: String, lotId: String, amount: BigDecimal,
                       maxBidAmount: BigDecimal, replyTo: ActorRef[AuctionResponse]) extends AuctionCommand with InProgressStateCommand {
    require(maxBidAmount > amount)
  }

  final case class WrappedLotActorResponse(response: LotActor.LotResponse) extends  AuctionCommand

  final case class AuctionCommandRejected(auctionId: String) extends AuctionResponse

  final case class Started(auctionId: String) extends AuctionResponse
  final case class Stopped(auctionId: String) extends AuctionResponse
  final case class LotCreated(auctionId: String, lotId: String) extends AuctionResponse
  final case class LotRemoved(auctionId: String, lotId: String) extends AuctionResponse
  final case class LotsRemoved(auctionId: String, lotIds: Seq[String]) extends AuctionResponse
  final case class LotDetails(auctionId: String, lotId: String, description: Option[String],
    currentTopBidder: Option[String], currentBidAmount: Option[BigDecimal])  extends  AuctionResponse
  final case class AggregatedLotDetails(lotDetails: Seq[LotDetails]) extends  AuctionResponse
  final case class BidAccepted(auctionId: String, userId: String, lotId: String) extends AuctionResponse
  final case class BidRejected(auctionId: String, userId: String, lotId: String) extends AuctionResponse
  final case class LotNotFound(auctionId: String, lotId: String) extends AuctionResponse

  def apply(id: String): Behavior[AuctionCommand] =
    Behaviors.withStash(100) { buffer=>
      Behaviors.setup(context => new AuctionActor(id, buffer, context).closed)
    }

}

class AuctionActor(id: String, buffer: StashBuffer[AuctionCommand], context: ActorContext[AuctionCommand]) {
  import AuctionActor._

  private var lotActors = Map[String, ActorRef[LotActor.LotCommand]]()

  private val lotActorResponseAdapter: ActorRef[LotActor.LotResponse] = context.messageAdapter(ref=> WrappedLotActorResponse(ref))
  private var idCounter = 0

  private def closed: Behavior[AuctionCommand] =
    Behaviors.receiveMessagePartial {
      case AddLot(maybeDescription, maybeMinBidAmount, replyTo)=>
        idCounter += 1
        val lotId = idCounter.toString
        val lotActor = context.spawn(LotActor(lotId, maybeDescription, maybeMinBidAmount, None), s"LotActor-$lotId")
        lotActors += (lotId -> lotActor)
        replyTo ! LotCreated(id, lotId)
        Behaviors.same
      case RemoveLot(lotId, replyTo)=>
        lotActors.partition(_._1 == lotId) match {
          case (remove, retain)=>
            lotActors = retain
            remove.headOption.foreach {
              case (_, lotActor)=>
                context.stop(lotActor)
                replyTo ! LotRemoved(id, lotId)
            }
        }
        Behaviors.same
      case RemoveAllLots(replyTo)=>
        lotActors.foreach {
          case (_, lotActor)=> context.stop(lotActor)
        }
        val idsToRemove = lotActors.keys.toSeq.sorted
        lotActors = Map()
        replyTo ! LotsRemoved(id, idsToRemove)
        Behaviors.same
      case Start(replyTo)=>
        replyTo ! Started(id)
        inProgress(replyTo)

      case other: InProgressStateCommand=>
        context.log.warn(s"Unable to process this message [$other] because AuctionActor $id is in Closed state.")
        other.replyTo ! AuctionCommandRejected(id)
        Behaviors.same
  }

  private def inProgress(replyTo: ActorRef[AuctionResponse]): Behavior[AuctionCommand] =
    Behaviors.receiveMessagePartial {
      case Bid(userId, lotId, amount, maxBidAmount, replyTo)=>
        lotActors.find(_._1 == lotId) match {
          case Some((_, lotActor))=>
            lotActor ! LotActor.Bid(userId, amount, maxBidAmount, lotActorResponseAdapter)
            inProgress(replyTo)
          case None=>
            replyTo ! AuctionActor.LotNotFound(id, lotId)
            Behaviors.same
        }

      case GetLot(lotId, replyTo)=>
        lotActors.find(_._1 == lotId) match {
          case Some((_, lotActor))=>
            lotActor ! LotActor.GetDetails(lotActorResponseAdapter)
            inProgress(replyTo)
          case None=>
            replyTo ! AuctionActor.LotNotFound(id, lotId)
            Behaviors.same
        }

      case WrappedLotActorResponse(response)=>
        response match {
          case LotActor.LotDetails(lotId, maybeDescription, maybeTopBidder, maybeCurrentBidAmount)=>
            replyTo ! LotDetails(id, lotId, maybeDescription, maybeTopBidder, maybeCurrentBidAmount)
          case LotActor.BidAccepted(userId, lotId)=>
            replyTo ! BidAccepted(id, userId, lotId)
          case LotActor.BidRejected(userId, lotId)=>
            replyTo ! BidRejected(id, userId, lotId)
        }
        Behaviors.same

      case GetAllLots(replyTo)=>
        lotActors.foreach(_._2 ! LotActor.GetDetails(lotActorResponseAdapter))
        gatheringAllLotDetails(Seq.empty[LotDetails], replyTo)

      case Stop(replyTo)=>
        replyTo ! Stopped(id)
        stopped(replyTo)

      case other: ClosedStateCommand=>
        context.log.warn(s"Unable to process this message [$other] because AuctionActor $id is in In-Progress state.")
        other.replyTo ! AuctionCommandRejected(id)
        Behaviors.same
    }


  private def gatheringAllLotDetails(lotDetailsReceived: Seq[LotDetails], replyTo: ActorRef[AuctionResponse]): Behavior[AuctionCommand] =
    Behaviors.receiveMessage {
      case WrappedLotActorResponse(LotActor.LotDetails(lotId, description, currentTopBidder, currentBidAmount))=>
        val gatheredLotDetails = lotDetailsReceived :+ LotDetails(id, lotId, description, currentTopBidder, currentBidAmount)
        if (gatheredLotDetails.size < lotActors.size) {
          gatheringAllLotDetails(gatheredLotDetails, replyTo)
        } else {
          //finished collecting responses from all my children, reply to my parent
          replyTo ! AggregatedLotDetails(gatheredLotDetails.sortBy(_.lotId))
          buffer.unstashAll(inProgress(replyTo))
        }

      case other=>
        buffer.stash(other)
        Behaviors.same
    }

  private def stopped(replyTo: ActorRef[AuctionResponse]): Behavior[AuctionCommand] = Behaviors.receiveMessage {
    case _=>
      context.log.warn(s"AuctionActor $id is already stopped and cannot process anymore commands.")
      replyTo ! AuctionCommandRejected(id)
      Behaviors.same
  }

}