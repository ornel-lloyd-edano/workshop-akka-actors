package io.scalac.auction

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

object LotActor {

  sealed trait LotCommand
  final case class Bid(amount: BigDecimal, replyTo: ActorRef[LotEvent]) extends LotCommand
  final case class SetMaxBid(amount: BigDecimal, replyTo: ActorRef[LotEvent]) extends LotCommand

  sealed trait LotEvent
  final case class LotCreated(lotId: String) extends LotEvent
  case object BidPosted extends LotEvent
  case object MaxBidApplied extends LotEvent

  def apply(id: String): Behavior[LotActor.LotCommand] =
    Behaviors.setup(context => new LotActor(id, context))
}

class LotActor(id: String, context: ActorContext[LotActor.LotCommand]) extends AbstractBehavior[LotActor.LotCommand](context) {
  import LotActor._

  override def onMessage(message: LotCommand): Behavior[LotCommand] = message match {
    case Bid(amount, replyTo)=>
      replyTo ! BidPosted
      this
    case SetMaxBid(amount, replyTo)=>
      replyTo ! MaxBidApplied
      this
  }
}