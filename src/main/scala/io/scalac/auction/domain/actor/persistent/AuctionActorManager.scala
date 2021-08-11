package io.scalac.auction.domain.actor.persistent

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.typed.scaladsl.ActorSource
import akka.stream.{Materializer, OverflowStrategy}
import io.scalac.auction.domain.actor.AuctionActor
import io.scalac.auction.domain.actor.AuctionActor.AuctionCommand
import io.scalac.auction.domain.model.{AuctionStates, AuctionStatus}
import io.scalac.serde.CborSerializable

import scala.concurrent.duration._

object AuctionActorManager {

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
  final case class ExpectResponse(replyTo: ActorRef[AuctionMgmtResponse]) extends AuctionMgmtResponse

  final case class GetStreamSource(replyTo: ActorRef[AuctionMgmtResponse]) extends AuctionMgmtCommand
  final case class StreamSource(source: Source[StreamActor.Protocol, ActorRef[StreamActor.Protocol]]) extends AuctionMgmtResponse
  object StreamActor {
    trait Protocol
    case class Message(msg: AuctionMgmtResponse) extends Protocol
    case object Complete extends Protocol
    case class Fail(ex: Exception) extends Protocol
  }

  final case class AuctionMgmtState(auctionActors: Map[String, (ActorRef[AuctionCommand], AuctionStatus)],
                                    idCounter: Int, replyTo: Option[ActorRef[AuctionMgmtResponse]] = None)

  val messageStreamSource: Source[StreamActor.Protocol, ActorRef[StreamActor.Protocol]] =
    ActorSource.actorRef[StreamActor.Protocol](
      completionMatcher = {
        case StreamActor.Complete =>
      },
      failureMatcher = {
        case StreamActor.Fail(ex) => ex
      }, bufferSize = 1000, overflowStrategy = OverflowStrategy.dropHead)

  private def applyCommand(state: AuctionMgmtState, cmd: AuctionMgmtCommand, replyTo: Option[ActorRef[AuctionMgmtResponse]])
                  (implicit context: ActorContext[AuctionActorManager.AuctionMgmtCommand],
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
          Effect.persist(ExpectResponse(replyTo))
            .thenRun(_=> auctionActor ! AuctionActor.AddLot(maybeDescription, maybeMinBidAmount, auctionActorMsgAdapter))

        case None=>
          context.log.warn(s"Unable to add a lot to auction $auctionId because auction was not found")
          Effect.reply(replyTo)(AuctionNotFound(auctionId))
      }

    case RemoveLot(auctionId, lotId, replyTo)=>
      state.auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          Effect.persist(ExpectResponse(replyTo))
            .thenRun(_=> auctionActor ! AuctionActor.RemoveLot(lotId, auctionActorMsgAdapter))

        case None=>
          context.log.warn(s"Unable to remove a lot from auction $auctionId because auction was not found")
          Effect.reply(replyTo)(AuctionNotFound(auctionId))
      }

    case RemoveAllLots(auctionId, replyTo)=>
      state.auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          Effect.persist(ExpectResponse(replyTo))
            .thenRun(_=> auctionActor ! AuctionActor.RemoveAllLots(auctionActorMsgAdapter))

        case _=>
          context.log.warn(s"Unable to remove lots from auction $auctionId because auction was not found")
          Effect.reply(replyTo)(AuctionNotFound(auctionId))
      }

    case GetLot(auctionId, lotId, replyTo)=>
      context.log.debug(s"Received GetLot(auctionId = $auctionId, lotId= $lotId)")
      state.auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          Effect.persist(ExpectResponse(replyTo))
            .thenRun(_=> auctionActor ! AuctionActor.GetLot(lotId, auctionActorMsgAdapter))

        case _=>
          context.log.warn(s"Unable to get lot at auction $auctionId because auction was not found")
          Effect.reply(replyTo)(AuctionNotFound(auctionId))
      }

    case GetAllLotsByAuction(auctionId, replyTo)=>
      state.auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          Effect.persist(ExpectResponse(replyTo))
            .thenRun(_=> auctionActor ! AuctionActor.GetAllLots(auctionActorMsgAdapter))

        case _=>
          context.log.warn(s"Unable to get all lots at auction $auctionId because auction was not found")
          Effect.reply(replyTo)(AuctionNotFound(auctionId))
      }

    case Start(auctionId, replyTo)=>
      state.auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          Effect.persist(ExpectResponse(replyTo))
            .thenRun(_=> auctionActor ! AuctionActor.Start(auctionActorMsgAdapter))

        case _=>
          context.log.warn(s"Unable to start auction $auctionId because it was not found")
          Effect.reply(replyTo)(AuctionNotFound(auctionId))
      }

    case Bid(userId, auctionId, lotId, amount, maxBidAmount, replyTo)=>
      state.auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          Effect.persist(ExpectResponse(replyTo))
            .thenRun(_=> auctionActor ! AuctionActor.Bid(userId, lotId, amount, maxBidAmount, auctionActorMsgAdapter))

        case _=>
          context.log.warn(s"Unable to bid lot at auction $auctionId because auction was not found")
          Effect.reply(replyTo)(AuctionNotFound(auctionId))
      }

    case Stop(auctionId, replyTo)=>
      state.auctionActors.get(auctionId) match {
        case Some((auctionActor, _))=>
          Effect.persist(ExpectResponse(replyTo))
            .thenRun(_=> auctionActor ! AuctionActor.Stop(auctionActorMsgAdapter))

        case _=>
          context.log.warn(s"Unable to stop auction $auctionId because it was not found")
          Effect.reply(replyTo)(AuctionNotFound(auctionId))
      }

    case WrappedAuctionActorResponse(response)=>
      val result = response match {
        case AuctionActor.LotCreated(auctionId, lotId)=>
          context.log.debug(s"AuctionActorMgr received LotCreated($auctionId, $lotId)")
          Right(LotAdded(auctionId, lotId))
        case AuctionActor.LotRemoved(auctionId, lotId)=>
          Right(LotRemoved(auctionId, lotId))
        case AuctionActor.LotsRemoved(auctionId, lotIds)=>
          Right(AllLotsRemoved(auctionId, lotIds))

        case AuctionActor.LotDetails(auctionId, lotId, description, currentTopBidder, currentBidAmount)=>
          context.log.debug(s"Replying LotDetails(auctionId = $auctionId, lotId= $lotId, price=${currentBidAmount})")
          Right(LotDetails(auctionId, lotId, description,
            currentTopBidder, currentBidAmount))

        case AuctionActor.AggregatedLotDetails(lotDetails)=>
          Right(AggregatedLotDetails(lotDetails.map(ld=>
            LotDetails(ld.auctionId, ld.lotId, ld.description, ld.currentTopBidder, ld.currentBidAmount))))

        case AuctionActor.BidAccepted(auctionId, userId, lotId, newPrice)=>
          val bidAccepted = BidAccepted(userId, lotId, auctionId, newPrice)
          streamActorRef ! StreamActor.Message(bidAccepted) //a side-effect here
          Right(bidAccepted)

        case AuctionActor.BidRejected(auctionId, userId, lotId, oldPrice)=>
          Right(BidRejected(userId, lotId, auctionId, oldPrice))

        case AuctionActor.LotNotFound(auctionId, lotId)=>
          Right(LotNotFound(auctionId, lotId))

        case AuctionActor.AuctionCommandRejected(_)=>
          Right(CommandRejected)

        case AuctionActor.Started(auctionId)=>
          Left(Started(auctionId))

        case AuctionActor.Stopped(auctionId)=>
          Left(Stopped(auctionId))
      }
      (state.replyTo, result) match {
        case (Some(ref), Right(response))=>
          Effect.none.thenReply(ref)(_=> response)
        case (Some(ref), Left(stateChangingResponse))=>
          Effect.persist(stateChangingResponse).thenReply(ref)(_=> stateChangingResponse)
        case _=>
          context.log.debug(s"ReplyTo is empty")
          Effect.noReply
      }
    case other=>
      context.log.info(s"Received unexpected command [$other]")
      Effect.none
  }

  private def applyEvent(state: AuctionMgmtState, evt: AuctionMgmtEvent)(implicit context: ActorContext[AuctionActorManager.AuctionMgmtCommand]): AuctionMgmtState = evt match {
    case Created(incrementedAuctionId)=>
      val auctionActor = context.spawn(AuctionActor(incrementedAuctionId), s"AuctionActor-$incrementedAuctionId")
      AuctionMgmtState(state.auctionActors + (incrementedAuctionId -> (auctionActor, AuctionStates.Closed)), incrementedAuctionId.toInt)

    case Started(auctionId)=>
      def startedState(actorRef: ActorRef[AuctionActor.AuctionCommand]) =
        state.copy(auctionActors = state.auctionActors + (auctionId ->  (actorRef, AuctionStates.Started)) )

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
        state.copy(auctionActors = state.auctionActors + (auctionId ->  (actorRef, AuctionStates.Stopped)))

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

    case ExpectResponse(replyTo)=>
      context.log.debug("Called an actor from command handler, need to hold replyTo in the state")
      state.copy(replyTo = Some(replyTo))

    case other=>
      context.log.info(s"Received unexpected event [$other]")
      state
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
