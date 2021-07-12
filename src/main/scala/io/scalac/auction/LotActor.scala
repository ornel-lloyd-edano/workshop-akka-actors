package io.scalac.auction

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

object LotActor {

  private case class BestBid(userId: String, amount: BigDecimal, maxBidAmount: BigDecimal)

  sealed trait LotCommand
  final case class Bid(userId: String, amount: BigDecimal, maxBidAmount: BigDecimal,  replyTo: ActorRef[LotResponse]) extends LotCommand {
    require(maxBidAmount > amount)
  }
  final case class GetDetails(replyTo: ActorRef[LotResponse]) extends LotCommand

  sealed trait LotResponse

  final case class BidAccepted(userId: String, lotId: String) extends LotResponse
  final case class BidRejected(userId: String, lotId: String) extends LotResponse
  final case class LotDetails(lotId: String, description: Option[String],
                              currentTopBidder: Option[String], currentBidAmount: Option[BigDecimal]) extends LotResponse

  def apply(id: String, description: Option[String], minAmount: Option[BigDecimal], maxAmount: Option[BigDecimal]): Behavior[LotActor.LotCommand] =
    Behaviors.setup(context => new LotActor(id, description, minAmount, maxAmount, context))
}

class LotActor(id: String, description: Option[String],
               minAmount: Option[BigDecimal], maxAmount: Option[BigDecimal],
               context: ActorContext[LotActor.LotCommand]) extends AbstractBehavior[LotActor.LotCommand](context) {
  import LotActor._

  private var currentBestBid:Option[BestBid] = None

  override def onMessage(message: LotCommand): Behavior[LotCommand] = message match {
    case Bid(newUserId, newBidAmount, newMaxBidAmount, replyTo)=>
      currentBestBid match {
        case Some(bestBid) if newBidAmount > bestBid.maxBidAmount && newMaxBidAmount > bestBid.maxBidAmount =>
          currentBestBid = Some(BestBid(newUserId, newBidAmount, newMaxBidAmount))
          replyTo ! BidAccepted(newUserId, id)
        case Some(_)=>
          replyTo ! BidRejected(newUserId, id)
        case None=>
          currentBestBid = Some(BestBid(newUserId, newBidAmount, newMaxBidAmount))
          replyTo ! BidAccepted(newUserId, id)
      }
      this

    case GetDetails(replyTo)=>
      replyTo ! LotDetails(id, description, currentBestBid.map(_.userId), currentBestBid.map(_.amount))
      this

  }
}