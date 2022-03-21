package io.scalac.auction.domain.actor.persistent

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import io.scalac.serde.CborSerializable
import scala.concurrent.duration._

object AuctionActor {

  sealed trait AuctionCommand extends CborSerializable
  sealed trait InProgressStateCommand {
    val replyTo: ActorRef[AuctionResponse]
  }
  sealed trait ClosedStateCommand {
    val replyTo: ActorRef[AuctionResponse]
  }

  sealed trait AuctionEvent extends CborSerializable
  sealed trait AuctionResponse extends AuctionEvent

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
    require(maxBidAmount >= amount)
  }

  final case class WrappedLotActorResponse(response: LotActor.LotResponse) extends  AuctionCommand

  final case class AuctionCommandRejected(auctionId: String) extends AuctionResponse

  final case class Started(auctionId: String) extends AuctionResponse
  final case class Stopped(auctionId: String) extends AuctionResponse
  final case class LotCreated(auctionId: String, lotId: String, description: Option[String], minBidAmount: Option[BigDecimal]) extends AuctionResponse
  final case class LotRemoved(auctionId: String, lotId: String) extends AuctionResponse
  final case class LotsRemoved(auctionId: String, lotIds: Seq[String]) extends AuctionResponse
  final case class LotDetails(auctionId: String, lotId: String, description: Option[String],
                              currentTopBidder: Option[String], currentBidAmount: Option[BigDecimal])  extends  AuctionResponse
  final case class AggregatedLotDetails(lotDetails: Seq[LotDetails]) extends  AuctionResponse
  final case class BidAccepted(auctionId: String, userId: String, lotId: String, price: BigDecimal) extends AuctionResponse
  final case class BidRejected(auctionId: String, userId: String, lotId: String, price: BigDecimal) extends AuctionResponse
  final case class LotNotFound(auctionId: String, lotId: String) extends AuctionResponse

  final case class ExpectResponse(replyTo: ActorRef[AuctionResponse],
                                  maybeBehaviorChange: Option[BehaviorState] = None,
                                  lotDetailsReceived: Seq[LotDetails] = Nil) extends AuctionResponse

  sealed trait BehaviorState
  object BehaviorStates {
    case object Closed extends BehaviorState
    case object InProgress extends BehaviorState
    case object GatheringResponses extends BehaviorState
    case object Stopped extends BehaviorState
  }
  final case class AuctionActorState(auctionId: String,
                                     lotIdCounter: Int = 0,
                                     lotActors: Map[String, ActorRef[LotActor.LotCommand]] = Map(),
                                     replyTo: Option[ActorRef[AuctionResponse]] = None,
                                     behaviorState: BehaviorState = BehaviorStates. Closed,
                                     lotDetailsReceived: Seq[LotDetails] = Nil)

  private def closedBehaviorCommands(state: AuctionActorState, cmd: AuctionCommand)
                                    (implicit context: ActorContext[AuctionCommand],
                                     lotActorResponseAdapter: ActorRef[LotActor.LotResponse]): Effect[AuctionEvent, AuctionActorState] = cmd match {
    case AddLot(maybeDescription, maybeMinBidAmount, replyTo)=>
      val newLotId = state.lotIdCounter + 1
      val created = LotCreated(state.auctionId, newLotId.toString, maybeDescription, maybeMinBidAmount)
      Effect.persist(created).thenReply(replyTo)(_=> created)

    case RemoveLot(lotId, replyTo)=>
      state.lotActors.find(_._1 == lotId) match {
        case Some(_)=>
          val removed = LotRemoved(state.auctionId, lotId)
          Effect.persist(removed).thenReply(replyTo)(_=> removed)
        case None=>
          Effect.reply(replyTo)(LotNotFound(state.auctionId, lotId))
      }

    case RemoveAllLots(replyTo)=>
      val removeAll = LotsRemoved(state.auctionId, state.lotActors.keys.toSeq.sorted)
      Effect.persist(removeAll).thenReply(replyTo)(_=> removeAll)

    case Start(replyTo)=>
      val started = Started(state.auctionId)
      Effect.persist(started).thenReply(replyTo)(_=> started)

    case Stop(replyTo)=>
      val stopped = Stopped(state.auctionId)
      Effect.persist(stopped).thenReply(replyTo)(_=> stopped)

    case other: InProgressStateCommand=>
      context.log.warn(s"Unable to process this message [$other] because AuctionActor ${state.auctionId} is in Closed state.")
      Effect.reply(other.replyTo)(AuctionCommandRejected(state.auctionId))
  }

  private def inProgressBehaviorCommands(state: AuctionActorState, cmd: AuctionCommand)
                                        (implicit context: ActorContext[AuctionCommand],
                                         lotActorResponseAdapter: ActorRef[LotActor.LotResponse]): Effect[AuctionEvent, AuctionActorState] =
    cmd match {
      case Bid(userId, lotId, amount, maxBidAmount, replyTo)=>
        state.lotActors.find(_._1 == lotId) match {
          case Some((_, lotActor))=>
            Effect.persist(ExpectResponse(replyTo))
              .thenRun(_=> lotActor ! LotActor.Bid(userId, amount, maxBidAmount, lotActorResponseAdapter))
          case None=>
            Effect.reply(replyTo)(LotNotFound(state.auctionId, lotId))
        }

      case GetLot(lotId, replyTo)=>
        state.lotActors.find(_._1 == lotId) match {
          case Some((_, lotActor))=>
            Effect.persist(ExpectResponse(replyTo)).thenRun(_=> lotActor ! LotActor.GetDetails(lotActorResponseAdapter))
          case None=>
            Effect.reply(replyTo)(LotNotFound(state.auctionId, lotId))
        }

      case GetAllLots(replyTo)=>
        Effect.persist(ExpectResponse(replyTo, Some(BehaviorStates.GatheringResponses)))
          .thenRun(_=> state.lotActors.foreach(_._2 ! LotActor.GetDetails(lotActorResponseAdapter)))

      case WrappedLotActorResponse(response)=>
        val result = response match {
          case LotActor.LotDetails(lotId, maybeDescription, maybeTopBidder, maybeCurrentBidAmount)=>
            LotDetails(state.auctionId, lotId, maybeDescription, maybeTopBidder, maybeCurrentBidAmount)
          case LotActor.BidAccepted(userId, lotId, newPrice, _)=>
            BidAccepted(state.auctionId, userId, lotId, newPrice)
          case LotActor.BidRejected(userId, lotId, oldPrice)=>
            BidRejected(state.auctionId, userId, lotId, oldPrice)
        }
        state.replyTo match {
          case Some(ref)=> Effect.reply(ref)(result)
          case None=> Effect.none
        }

      case Start(replyTo)=>
        val started = Started(state.auctionId)
        Effect.reply(replyTo)(started)

      case Stop(replyTo)=>
        val stopped = Stopped(state.auctionId)
        Effect.persist(stopped).thenReply(replyTo)(_=> stopped)

      case other: ClosedStateCommand=>
        context.log.warn(s"Unable to process this message [$other] because AuctionActor ${state.auctionId} is in In-Progress state.")
        Effect.reply(other.replyTo)(AuctionCommandRejected(state.auctionId))
    }


  private def gatheringResponsesBehaviorCommands(state: AuctionActorState, cmd: AuctionCommand)
                                                (implicit context: ActorContext[AuctionCommand],
                                                 lotActorResponseAdapter: ActorRef[LotActor.LotResponse]): Effect[AuctionEvent, AuctionActorState] =
    cmd match {
      case WrappedLotActorResponse(LotActor.LotDetails(lotId, description, currentTopBidder, currentBidAmount))=>
        val gatheredLotDetails = state.lotDetailsReceived :+ LotDetails(state.auctionId, lotId, description, currentTopBidder, currentBidAmount)
        (gatheredLotDetails.size < state.lotActors.size, state.replyTo) match {
          case (true, Some(ref))=>
            Effect.persist(ExpectResponse(replyTo = ref, lotDetailsReceived = gatheredLotDetails))
          case (false, Some(ref))=>
            Effect.persist(ExpectResponse(replyTo = ref, maybeBehaviorChange = Some(BehaviorStates.InProgress), lotDetailsReceived = Nil))
              .thenReply(ref) { (s: AuctionActorState)=> AggregatedLotDetails(gatheredLotDetails.sortBy(_.lotId)) }
              .thenUnstashAll()

          case (_, None)=>
            context.log.warn(s"Empty replyTo state in gatheringResponsesBehaviorCommands")
            Effect.none
        }
      case other=>
        context.log.warn(s"Stashing this message [$other] because AuctionActor ${state.auctionId} is still gathering responses.")
        Effect.stash()
    }

  private def stoppedBehaviorCommands(state: AuctionActorState, cmd: AuctionCommand)
                                                (implicit context: ActorContext[AuctionCommand],
                                                 lotActorResponseAdapter: ActorRef[LotActor.LotResponse]): Effect[AuctionEvent, AuctionActorState] =
    cmd match {
      case Stop(replyTo)=>
        Effect.reply(replyTo)(Stopped(state.auctionId))
      case _=>
        context.log.warn(s"AuctionActor ${state.auctionId} is already stopped and cannot process anymore commands.")
        Behaviors.same
        state.replyTo match {
          case Some(ref)=> Effect.reply(ref)(AuctionCommandRejected(state.auctionId))
          case None=> Effect.none
        }
    }

  private def applyCommand(state: AuctionActorState, cmd: AuctionCommand)
                          (implicit context: ActorContext[AuctionCommand],
                           lotActorResponseAdapter: ActorRef[LotActor.LotResponse]): Effect[AuctionEvent, AuctionActorState] =
    state.behaviorState match {
      case BehaviorStates.Closed=> closedBehaviorCommands(state, cmd)
      case BehaviorStates.InProgress=> inProgressBehaviorCommands(state, cmd)
      case BehaviorStates.GatheringResponses=> gatheringResponsesBehaviorCommands(state, cmd)
      case BehaviorStates.Stopped=> stoppedBehaviorCommands(state, cmd)
    }


  private def closedBehaviorEvents(state: AuctionActorState, evt: AuctionEvent)
                                  (implicit context: ActorContext[AuctionCommand]): AuctionActorState =
    evt match {
      case LotCreated(_, incrementedLotId, description, minBidAmount)=>
        val newLotActor = context.spawn(LotActor(incrementedLotId, description, minBidAmount, None), s"LotActor-$incrementedLotId")
        state.copy(lotActors = state.lotActors + (incrementedLotId -> newLotActor), lotIdCounter = incrementedLotId.toInt)
      case LotRemoved(_, lotId)=>
        state.lotActors.find(_._1 == lotId) match {
          case Some((_, lotActor))=>
            context.stop(lotActor)
            state.copy( lotActors = state.lotActors - lotId)
          case None=>
            state
        }
      case LotsRemoved(_, lotIds)=>
        state.lotActors.collect {
          case ((id, ref)) if lotIds.contains(id)=> ref
        }.foreach(context.stop(_))
        state.copy(lotActors = Map())
      case Started(_)=>
        state.copy(behaviorState = BehaviorStates.InProgress)
      case Stopped(_)=>
        state.copy(behaviorState = BehaviorStates.Stopped)
      case other=>
        context.log.warn(s"Received unexpected message [$other] in closedBehaviorEvents")
        state
    }

  private def inProgressBehaviorEvents(state: AuctionActorState, evt: AuctionEvent)
                                  (implicit context: ActorContext[AuctionCommand]): AuctionActorState =
    evt match {
      case ExpectResponse(replyTo, Some(behaviorChange), _)=>
        state.copy(replyTo = Some(replyTo), behaviorState = behaviorChange)
      case ExpectResponse(replyTo, _, _)=>
        state.copy(replyTo = Some(replyTo))
      case Stopped(_)=>
        state.copy(behaviorState = BehaviorStates.Stopped)
      case other=>
        context.log.warn(s"Received unexpected message [$other] in inProgressBehaviorEvents")
        state
    }

  private def gatheringResponseBehaviorEvents(state: AuctionActorState, evt: AuctionEvent)
                                             (implicit context: ActorContext[AuctionCommand]): AuctionActorState =
    evt match {
      case ExpectResponse(_, None, lotDetailsReceived)=>
        state.copy(lotDetailsReceived = lotDetailsReceived)
      case ExpectResponse(_, Some(BehaviorStates.InProgress), Nil)=>
        state.copy(behaviorState = BehaviorStates.InProgress, lotDetailsReceived = Nil)
      case other=>
        context.log.warn(s"Received unexpected message [$other] in gatheringResponseBehaviorEvents")
        state
    }

  private def applyEvent(state: AuctionActorState, evt: AuctionEvent)
                        (implicit context: ActorContext[AuctionCommand]): AuctionActorState =
    state.behaviorState match {
      case BehaviorStates.Closed=> closedBehaviorEvents(state, evt)
      case BehaviorStates.InProgress=> inProgressBehaviorEvents(state, evt)
      case BehaviorStates.GatheringResponses=> gatheringResponseBehaviorEvents(state, evt)
      case BehaviorStates.Stopped=> state
    }

  def apply(id: String): Behavior[AuctionCommand] =
    Behaviors.withStash(100) { buffer=>
      Behaviors.setup{ implicit context =>
        implicit val lotActorResponseAdapter: ActorRef[LotActor.LotResponse] =
          context.messageAdapter(ref=> WrappedLotActorResponse(ref))

        EventSourcedBehavior[AuctionCommand, AuctionEvent, AuctionActorState](
          PersistenceId("AuctionActor", id),
          AuctionActorState(id),
          (state, command) => applyCommand(state, command),
          (state, event) => applyEvent(state, event))
          .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
          .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
      }
    }

}