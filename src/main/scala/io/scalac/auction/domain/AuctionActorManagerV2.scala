package io.scalac.auction.domain

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.typed.scaladsl.ActorSource
import io.scalac.auction.domain.AuctionActor.AuctionCommand
import io.scalac.auction.domain.model.{AuctionStates, AuctionStatus}
import io.scalac.serde.CborSerializable

import scala.concurrent.duration._

object AuctionActorManagerV2 {

  sealed trait AuctionMgmtCommand extends CborSerializable
  sealed trait AuctionMgmtEvent
  sealed trait AuctionMgmtResponse extends AuctionMgmtEvent with CborSerializable

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

  final case class AuctionMgmtState(auctionActors: Map[String, (ActorRef[AuctionCommand], AuctionStatus)], idCounter: Int)

  val messageStreamSource: Source[StreamActor.Protocol, ActorRef[StreamActor.Protocol]] =
    ActorSource.actorRef[StreamActor.Protocol](
      completionMatcher = {
        case StreamActor.Complete =>
      },
      failureMatcher = {
        case StreamActor.Fail(ex) => ex
      }, bufferSize = 1000, overflowStrategy = OverflowStrategy.dropHead)

  private def applyCommand(state: AuctionMgmtState, cmd: AuctionMgmtCommand, replyTo: Option[ActorRef[AuctionMgmtResponse]])
                  (implicit context: ActorContext[AuctionActorManagerV2.AuctionMgmtCommand],
                   auctionActorMsgAdapter: ActorRef[AuctionActor.AuctionResponse],
                   streamActorRef: ActorRef[StreamActor.Protocol]): Effect[AuctionMgmtEvent, AuctionMgmtState] = cmd match {

    case Create(replyTo)=>
      val incrementedAuctionId = (state.idCounter + 1).toString
      val created = Created(incrementedAuctionId)
      Effect.persist(created).thenReply(replyTo)(_=> created)

    case GetAllAuctions(replyTo)=>
      val results = state.auctionActors.map {
        case (id, (_, status))=>
          AuctionDetail(id, status)
      }.toSeq.sortBy(_.id)
      Effect.reply(replyTo)(AggregatedAuctionDetails(results)) //query has no side-effect, no need to have a corresponding section in applyEvent

    case AddLot(auctionId, maybeDescription, maybeMinBidAmount, replyTo)=>
      state.auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          auctionActor ! AuctionActor.AddLot(maybeDescription, maybeMinBidAmount, auctionActorMsgAdapter)
          applyCommand(state, cmd, Some(replyTo))
        case None=>
          context.log.warn(s"Unable to add a lot to auction $auctionId because auction was not found")
          Effect.reply(replyTo)(AuctionNotFound(auctionId))
      }

    case RemoveLot(auctionId, lotId, replyTo)=>
      state.auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          auctionActor ! AuctionActor.RemoveLot(lotId, auctionActorMsgAdapter)
          applyCommand(state, cmd, Some(replyTo))
        case None=>
          context.log.warn(s"Unable to remove a lot from auction $auctionId because auction was not found")
          Effect.reply(replyTo)(AuctionNotFound(auctionId))
      }

    case RemoveAllLots(auctionId, replyTo)=>
      state.auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          auctionActor ! AuctionActor.RemoveAllLots(auctionActorMsgAdapter)
          applyCommand(state, cmd, Some(replyTo))
        case _=>
          context.log.warn(s"Unable to remove lots from auction $auctionId because auction was not found")
          Effect.reply(replyTo)(AuctionNotFound(auctionId))
      }

    case GetLot(auctionId, lotId, replyTo)=>
      context.log.debug(s"Received GetLot(auctionId = $auctionId, lotId= $lotId)")
      state.auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          auctionActor ! AuctionActor.GetLot(lotId, auctionActorMsgAdapter)
          applyCommand(state, cmd, Some(replyTo))
        case _=>
          context.log.warn(s"Unable to get lot at auction $auctionId because auction was not found")
          Effect.reply(replyTo)(AuctionNotFound(auctionId))
      }

    case GetAllLotsByAuction(auctionId, replyTo)=>
      state.auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          auctionActor ! AuctionActor.GetAllLots(auctionActorMsgAdapter)
          applyCommand(state, cmd, Some(replyTo))
        case _=>
          context.log.warn(s"Unable to get all lots at auction $auctionId because auction was not found")
          Effect.reply(replyTo)(AuctionNotFound(auctionId))
      }

    case Start(auctionId, replyTo)=>
      state.auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          auctionActor ! AuctionActor.Start(auctionActorMsgAdapter)
          applyCommand(state, cmd, Some(replyTo))
        case _=>
          context.log.warn(s"Unable to start auction $auctionId because it was not found")
          Effect.reply(replyTo)(AuctionNotFound(auctionId))
      }

    case Bid(userId, auctionId, lotId, amount, maxBidAmount, replyTo)=>
      state.auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          auctionActor ! AuctionActor.Bid(userId, lotId, amount, maxBidAmount, auctionActorMsgAdapter)
          applyCommand(state, cmd, Some(replyTo))
        case _=>
          context.log.warn(s"Unable to bid lot at auction $auctionId because auction was not found")
          Effect.reply(replyTo)(AuctionNotFound(auctionId))
      }

    case Stop(auctionId, replyTo)=>
      state.auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          auctionActor ! AuctionActor.Stop(auctionActorMsgAdapter)
          applyCommand(state, cmd, Some(replyTo))
        case _=>
          context.log.warn(s"Unable to stop auction $auctionId because it was not found")
          Effect.reply(replyTo)(AuctionNotFound(auctionId))
      }

    case WrappedAuctionActorResponse(response)=>
      val result = response match {
        case AuctionActor.LotCreated(auctionId, lotId)=>
          LotAdded(auctionId, lotId)

        case AuctionActor.LotRemoved(auctionId, lotId)=>
          LotRemoved(auctionId, lotId)
        case AuctionActor.LotsRemoved(auctionId, lotIds)=>
          AllLotsRemoved(auctionId, lotIds)

        case AuctionActor.LotDetails(auctionId, lotId, description, currentTopBidder, currentBidAmount)=>
          context.log.debug(s"Replying LotDetails(auctionId = $auctionId, lotId= $lotId, price=${currentBidAmount})")
          LotDetails(auctionId, lotId, description,
            currentTopBidder, currentBidAmount)

        case AuctionActor.AggregatedLotDetails(lotDetails)=>
          AggregatedLotDetails(lotDetails.map(ld=>
            LotDetails(ld.auctionId, ld.lotId, ld.description, ld.currentTopBidder, ld.currentBidAmount)))

        case AuctionActor.Started(auctionId)=>
          Started(auctionId)

        case AuctionActor.Stopped(auctionId)=>
          Stopped(auctionId)

        case AuctionActor.BidAccepted(auctionId, userId, lotId, newPrice)=>
          val bidAccepted = BidAccepted(userId, lotId, auctionId, newPrice)
          streamActorRef ! StreamActor.Message(bidAccepted)
          bidAccepted

        case AuctionActor.BidRejected(auctionId, userId, lotId, oldPrice)=>
          BidRejected(userId, lotId, auctionId, oldPrice)

        case AuctionActor.LotNotFound(auctionId, lotId)=>
          LotNotFound(auctionId, lotId)

        case AuctionActor.AuctionCommandRejected(_)=>
          CommandRejected
      }
      Effect.persist(result).thenRun(_=> replyTo.foreach(ref=> ref ! result))
  }

  private def applyEvent(state: AuctionMgmtState, evt: AuctionMgmtEvent)(implicit context: ActorContext[AuctionActorManagerV2.AuctionMgmtCommand]): AuctionMgmtState = evt match {
    case Created(incrementedAuctionId)=>
      val auctionActor = context.spawn(AuctionActor(incrementedAuctionId), s"AuctionActor-$incrementedAuctionId")
      AuctionMgmtState(state.auctionActors + (incrementedAuctionId -> (auctionActor, AuctionStates.Closed)), incrementedAuctionId.toInt)

    case Started(auctionId)=>
      def startedState(actorRef: ActorRef[AuctionActor.AuctionCommand]) =
        AuctionMgmtState(state.auctionActors + (auctionId ->  (actorRef, AuctionStates.Started)) , state.idCounter)

      state.auctionActors.find(_._1 == auctionId).map(_._2) match {
        case Some((actorRef, AuctionStates.Closed))=>
          startedState(actorRef)
        case Some((actorRef, otherState))=>
          context.log.warn(s"Inconsistent state. Received [Started($auctionId)] but auction [$auctionId] is $otherState.")
          startedState(actorRef)
        case None=>
          context.log.warn(s"Inconsistent state. Received [Started($auctionId)] but auction [$auctionId] not found in the state.")
          state
      }

    case Stopped(auctionId)=>
      def stoppedState(actorRef: ActorRef[AuctionActor.AuctionCommand]) =
        AuctionMgmtState(state.auctionActors + (auctionId ->  (actorRef, AuctionStates.Stopped)) , state.idCounter)

      state.auctionActors.find(_._1 == auctionId).map(_._2) match {
        case Some((actorRef, AuctionStates.Started))=>
          stoppedState(actorRef)
        case Some((actorRef, otherState))=>
          context.log.warn(s"Inconsistent state. Received [Stopped($auctionId)] but auction [$auctionId] is $otherState.")
          stoppedState(actorRef)
        case None=>
          context.log.warn(s"Inconsistent state. Received [Stopped($auctionId)] but auction [$auctionId] not found in the state.")
          state
      }
  }

  def apply(id: String): Behavior[AuctionMgmtCommand] =
    Behaviors.setup(implicit context => {
      implicit val auctionActorMsgAdapter: ActorRef[AuctionActor.AuctionResponse] = context.messageAdapter(WrappedAuctionActorResponse(_))
      implicit val streamActorRef: ActorRef[StreamActor.Protocol] = messageStreamSource
        .collect {
          case StreamActor.Message(msg) => msg
        }.to(Sink.ignore).run()(Materializer(context.system))

      EventSourcedBehavior[AuctionMgmtCommand, AuctionMgmtEvent, AuctionMgmtState](
        PersistenceId("AuctionMgmtActor", id),
        AuctionMgmtState(Map(), 0),
        (state, command) => applyCommand(state, command, None),
        (state, event) => applyEvent(state, event))
        .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
        .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
  })

}
