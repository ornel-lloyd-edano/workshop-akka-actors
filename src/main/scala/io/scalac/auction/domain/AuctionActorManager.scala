package io.scalac.auction.domain

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{CompletionStrategy, Materializer, OverflowStrategy}
import akka.stream.typed.scaladsl.ActorSource
import io.scalac.auction.domain.AuctionActor.AuctionCommand
import io.scalac.auction.domain.model.{AuctionStates, AuctionStatus}

object AuctionActorManager {

  sealed trait AuctionMgmtCommand
  sealed trait AuctionMgmtResponse

  final case class Create(replyTo: ActorRef[AuctionMgmtResponse]) extends AuctionMgmtCommand
  final case class GetAllAuctions(replyTo: ActorRef[AuctionMgmtResponse]) extends AuctionMgmtCommand
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
  case object ReachedEnd extends AuctionMgmtCommand
  final case class FailureOccured(exception: Exception) extends AuctionMgmtCommand

  final case class Created(auctionId: String) extends AuctionMgmtResponse
  final case class AuctionDetail(id: String, status: AuctionStatus)
  final case class AggregatedAuctionDetails(auctionDetails: Seq[AuctionDetail]) extends AuctionMgmtResponse
  final case class Started(auctionId: String) extends AuctionMgmtResponse
  final case class Stopped(auctionId: String) extends AuctionMgmtResponse
  final case class LotAdded(auctionId: String, lotId: String) extends AuctionMgmtResponse
  final case class LotRemoved(auctionId: String, lotId: String) extends AuctionMgmtResponse
  final case class AllLotsRemoved(auctionId: String, lotIds: Seq[String]) extends AuctionMgmtResponse
  final case class LotDetails(auctionId: String, lotId: String, description: Option[String],
                              currentTopBidder: Option[String], currentBidAmount: Option[BigDecimal]) extends AuctionMgmtResponse
  final case class AggregatedLotDetails(lotDetails: Seq[LotDetails]) extends  AuctionMgmtResponse
  final case class BidAccepted(userId: String, lotId: String, auctionId: String, price: BigDecimal) extends AuctionMgmtResponse
  final case class BidRejected(userId: String, lotId: String,  auctionId: String, price: BigDecimal) extends AuctionMgmtResponse
  final case class AuctionNotFound(auctionId: String) extends AuctionMgmtResponse
  final case class LotNotFound(auctionId: String, lotId: String) extends AuctionMgmtResponse
  case object CommandRejected extends AuctionMgmtResponse

  final case class GetStreamSource(replyTo: ActorRef[AuctionMgmtResponse]) extends AuctionMgmtCommand
  final case class StreamSource(source: Source[StreamActor.Protocol, ActorRef[StreamActor.Protocol]]) extends AuctionMgmtResponse
  object StreamActor {
    trait Protocol
    case class Message(msg: AuctionMgmtResponse) extends Protocol
    case object Complete extends Protocol
    case class Fail(ex: Exception) extends Protocol
  }

  def apply(): Behavior[AuctionMgmtCommand] = Behaviors.withStash(100) { buffer=>
    Behaviors.setup(context => new AuctionActorManager(buffer, context).running(None))
  }

}

class AuctionActorManager private(buffer: StashBuffer[AuctionActorManager.AuctionMgmtCommand],
                                  context: ActorContext[AuctionActorManager.AuctionMgmtCommand]) {
  import AuctionActorManager._

  private var auctionActors = Map[String, (ActorRef[AuctionCommand], AuctionStatus)]()
  private var idCounter: Int = 0
  val auctionActorMsgAdapter: ActorRef[AuctionActor.AuctionResponse] = context.messageAdapter(WrappedAuctionActorResponse(_))

  val source: Source[StreamActor.Protocol, ActorRef[StreamActor.Protocol]] =
    ActorSource.actorRef[StreamActor.Protocol](
      completionMatcher = {
        case StreamActor.Complete =>
      },
      failureMatcher = {
        case StreamActor.Fail(ex) => ex
      }, bufferSize = 1000, overflowStrategy = OverflowStrategy.dropHead)

  private val streamActorRef = source
    .collect {
      case StreamActor.Message(msg) => msg
    }.to(Sink.ignore).run()(Materializer(context.system))

  def running(replyTo: Option[ActorRef[AuctionMgmtResponse]]): Behavior[AuctionActorManager.AuctionMgmtCommand] =  Behaviors.receiveMessagePartial {
    case GetStreamSource(replyTo)=>
      replyTo ! StreamSource(source)
      Behaviors.same

    case Create(replyTo)=>
      idCounter += 1
      val auctionId = idCounter.toString
      val auctionActor = context.spawn(AuctionActor(auctionId), s"AuctionActor-$auctionId")
      auctionActors += (auctionId -> (auctionActor, AuctionStates.Closed))
      replyTo ! Created(auctionId)
      running(Some(replyTo))

    case GetAllAuctions(replyTo)=>
      val results = auctionActors.map {
        case (id, (_, status))=>
          AuctionDetail(id, status)
      }.toSeq.sortBy(_.id)

      replyTo ! AggregatedAuctionDetails(results)
      Behaviors.same

    case AddLot(auctionId, maybeDescription, maybeMinBidAmount, replyTo)=>
      auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          auctionActor ! AuctionActor.AddLot(maybeDescription, maybeMinBidAmount, auctionActorMsgAdapter)
        case None=>
          context.log.warn(s"Unable to add a lot to auction $auctionId because auction was not found")
          replyTo ! AuctionNotFound(auctionId)
      }
      running(Some(replyTo))

    case RemoveLot(auctionId, lotId, replyTo)=>
      auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          auctionActor ! AuctionActor.RemoveLot(lotId, auctionActorMsgAdapter)
        case None=>
          context.log.warn(s"Unable to remove a lot from auction $auctionId because auction was not found")
          replyTo ! AuctionNotFound(auctionId)
      }
      running(Some(replyTo))

    case RemoveAllLots(auctionId, replyTo)=>
      auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          auctionActor ! AuctionActor.RemoveAllLots(auctionActorMsgAdapter)
        case _=>
          context.log.warn(s"Unable to remove lots from auction $auctionId because auction was not found")
          replyTo ! AuctionNotFound(auctionId)
      }
      running(Some(replyTo))

    case Start(auctionId, replyTo)=>
      auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          auctionActor ! AuctionActor.Start(auctionActorMsgAdapter)
        case _=>
          context.log.warn(s"Unable to start auction $auctionId because it was not found")
          replyTo ! AuctionNotFound(auctionId)
      }
      running(Some(replyTo))

    case Bid(userId, auctionId, lotId, amount, maxBidAmount, replyTo)=>
      auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          auctionActor ! AuctionActor.Bid(userId, lotId, amount, maxBidAmount, auctionActorMsgAdapter)
        case _=>
          context.log.warn(s"Unable to bid lot at auction $auctionId because auction was not found")
          replyTo ! AuctionNotFound(auctionId)
      }
      running(Some(replyTo))

    case GetLot(auctionId, lotId, replyTo)=>
      context.log.debug(s"Received GetLot(auctionId = $auctionId, lotId= $lotId)")
      auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          auctionActor ! AuctionActor.GetLot(lotId, auctionActorMsgAdapter)
        case _=>
          context.log.warn(s"Unable to get lot at auction $auctionId because auction was not found")
          replyTo ! AuctionNotFound(auctionId)
      }
      running(Some(replyTo))

    case GetAllLotsByAuction(auctionId, replyTo)=>
      auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          auctionActor ! AuctionActor.GetAllLots(auctionActorMsgAdapter)
        case _=>
          context.log.warn(s"Unable to get all lots at auction $auctionId because auction was not found")
          replyTo ! AuctionNotFound(auctionId)
      }
      running(Some(replyTo))

    case Stop(auctionId, replyTo)=>
      auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
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
          context.log.debug(s"Replying LotDetails(auctionId = $auctionId, lotId= $lotId, price=${currentBidAmount})")
          replyTo.foreach(_ ! LotDetails(auctionId, lotId, description,
            currentTopBidder, currentBidAmount))
        case AuctionActor.AggregatedLotDetails(lotDetails)=>
          replyTo.foreach(_ ! AggregatedLotDetails(lotDetails.map(ld=>
            LotDetails(ld.auctionId, ld.lotId, ld.description, ld.currentTopBidder, ld.currentBidAmount))))
        case AuctionActor.Started(auctionId)=>
          auctionActors.find(_._1 == auctionId).foreach {
            case (_, (actorRef, _))=>
              auctionActors += (auctionId -> (actorRef, AuctionStates.Started))
          }
          replyTo.foreach(_ ! Started(auctionId))
        case AuctionActor.Stopped(auctionId)=>
          auctionActors.find(_._1 == auctionId).foreach {
            case (_, (actorRef, _))=>
              auctionActors += (auctionId -> (actorRef, AuctionStates.Stopped))
          }
          replyTo.foreach(_ ! Stopped(auctionId))
        case AuctionActor.BidAccepted(auctionId, userId, lotId, newPrice)=>
          val bidAccepted = BidAccepted(userId, lotId, auctionId, newPrice)
          streamActorRef ! StreamActor.Message(bidAccepted)
          replyTo.foreach(_ ! bidAccepted)
        case AuctionActor.BidRejected(auctionId, userId, lotId, oldPrice)=>
          replyTo.foreach(_ ! BidRejected(userId, lotId, auctionId, oldPrice))
        case AuctionActor.LotNotFound(auctionId, lotId)=>
          replyTo.foreach(_ ! LotNotFound(auctionId, lotId))
        case AuctionActor.AuctionCommandRejected(_)=>
          replyTo.foreach(_ ! CommandRejected)
      }
      Behaviors.same

  }

}
