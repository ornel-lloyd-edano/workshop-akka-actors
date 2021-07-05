package io.scalac.auction

import java.util.UUID

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import io.scalac.auction.LotActor.{LotCommand, LotCreated, LotEvent}

object AuctionActor {

  sealed trait AuctionCommand

  final case class AddLot(replyTo: ActorRef[LotEvent], description: Option[String], minBid: Option[BigDecimal], maxBid: Option[BigDecimal]) extends AuctionCommand

  final case class RemoveLot(lotId: String) extends AuctionCommand
  case object RemoveAllLots extends AuctionCommand
  final case class GetLot(lotId: String) extends AuctionCommand
  case object GetAllLots extends AuctionCommand

  final case class Bid(lotId: String, amount: BigDecimal) extends AuctionCommand
  final case class SetMaxBid(lotId: String, amount: BigDecimal) extends AuctionCommand


  def apply(id: String): Behavior[AuctionActor.AuctionCommand] =
    Behaviors.setup(context => new AuctionActor(id, context))
}

class AuctionActor(id: String, context: ActorContext[AuctionActor.AuctionCommand]) extends AbstractBehavior[AuctionActor.AuctionCommand](context) {
  import AuctionActor._

  var lotActors = Map[String, Behavior[LotCommand]]()

  override def onMessage(message: AuctionCommand): Behavior[AuctionCommand] = message match {

    case AddLot(replyTo, maybeDescription, maybeMinBid, maybeMaxBid)=>
      val lotId = UUID.randomUUID().toString
      val lotActor = LotActor(lotId)
      lotActors += Tuple2(lotId, lotActor)
      replyTo ! LotCreated(lotId)
      this

    case RemoveLot(lotId)=>
      this

    case RemoveAllLots=>
      this

    case GetLot(lotId)=>
      this

    case GetAllLots=>
      this

    case Bid(lotId: String, amount: BigDecimal)=>
      this

    case SetMaxBid(lotId: String, amount: BigDecimal)=>
      this
  }
}