package io.scalac.auction.domain.actor.persistent

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import io.scalac.serde.CborSerializable
import scala.concurrent.duration._

object LotActor {

  final case class BestBid(userId: String, amount: BigDecimal, maxBidAmount: BigDecimal)

  sealed trait LotCommand extends CborSerializable
  sealed trait LotEvent extends CborSerializable
  sealed trait LotResponse extends LotEvent

  final case class Bid(userId: String, amount: BigDecimal, maxBidAmount: BigDecimal,  replyTo: ActorRef[LotResponse]) extends LotCommand {
    require(maxBidAmount >= amount)
  }
  final case class GetDetails(replyTo: ActorRef[LotResponse]) extends LotCommand

  final case class BidAccepted(userId: String, lotId: String, price: BigDecimal, max: BigDecimal) extends LotResponse
  final case class BidRejected(userId: String, lotId: String, price: BigDecimal) extends LotResponse
  final case class LotDetails(lotId: String, description: Option[String],
                              currentTopBidder: Option[String], currentBidAmount: Option[BigDecimal]) extends LotResponse


  final case class LotActorState(lotId: String, description: Option[String] = None, minAmount: Option[BigDecimal] = None,
                                 maxAmount: Option[BigDecimal] = None, currentBestBid: Option[BestBid] = None)

  private def applyCommand(state: LotActorState, cmd: LotCommand)(implicit context: ActorContext[LotCommand]): Effect[LotEvent, LotActorState] =
    cmd match {
      case Bid(bidderId, bidAmount, maxBidAmount, replyTo)=>
        state.currentBestBid match {
          case Some(BestBid(_, oldAmount, oldMaxAmount)) if bidAmount <= oldMaxAmount || maxBidAmount <= oldMaxAmount =>
            Effect.reply(replyTo)(BidRejected(bidderId, state.lotId, oldAmount))
          case _=>
            val bidAccepted = BidAccepted(bidderId, state.lotId, bidAmount, maxBidAmount)
            Effect.persist(bidAccepted).thenReply(replyTo)(_=> bidAccepted)
          /*case Some(bestBid) if bidAmount > bestBid.maxBidAmount && maxBidAmount > bestBid.maxBidAmount =>
            val bidAccepted = BidAccepted(bidderId, state.lotId, bidAmount, maxBidAmount)
            Effect.persist(bidAccepted).thenReply(replyTo)(_=> bidAccepted)
          case Some(BestBid(_, oldAmount, _))=>
            Effect.reply(replyTo)(BidRejected(bidderId, state.lotId, oldAmount))
          case None=>
            val bidAccepted = BidAccepted(bidderId, state.lotId, bidAmount, maxBidAmount)
            Effect.persist(bidAccepted).thenReply(replyTo)(_=> bidAccepted)*/
        }

      case GetDetails(replyTo)=>
        Effect.reply(replyTo)(LotDetails(state.lotId, state.description, state.currentBestBid.map(_.userId), state.currentBestBid.map(_.amount)))
      case other=>
        context.log.warn(s"Received unexpected message [$other] in applyCommand")
        Effect.none
    }

  private def applyEvent(state: LotActorState, evt: LotEvent)(implicit context: ActorContext[LotCommand]): LotActorState =
    evt match {
      case BidAccepted(bidderId, _, bidAmount, maxBidAmount) =>
        state.copy(currentBestBid = Some(BestBid(bidderId, bidAmount, maxBidAmount)))
      case other=>
        context.log.warn(s"Received unexpected message [$other] in applyEvent")
        state
    }

  def apply(id: String, description: Option[String] = None, minAmount: Option[BigDecimal] = None,
            maxAmount: Option[BigDecimal] = None): Behavior[LotCommand] =
      Behaviors.setup { implicit context =>
        EventSourcedBehavior[LotCommand, LotEvent, LotActorState](
          PersistenceId("LotActor", id),
          LotActorState(id, description, minAmount, maxAmount),
          (state, command) => applyCommand(state, command),
          (state, event) => applyEvent(state, event))
          .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
          .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
      }

}
