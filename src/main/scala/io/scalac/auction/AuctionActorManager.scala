package io.scalac.auction

import java.util.UUID

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import io.scalac.auction.AuctionActor._

object AuctionActorManager {

  sealed trait AuctionMgmtCommand
  final case class Create(returnId: String=> String) extends AuctionMgmtCommand
  final case class Start(id: String) extends AuctionMgmtCommand
  final case class Stop(id: String) extends AuctionMgmtCommand

  var auctionActors = Map[String, Behavior[AuctionCommand]]()

  val manageAuction: Behavior[AuctionMgmtCommand] = Behaviors.receiveMessage {
    case Create(returnId)=>
      val auctionId = UUID.randomUUID().toString
      val auctionActor = AuctionActor(auctionId)
      auctionActors += Tuple2(auctionId, auctionActor)
      returnId(auctionId)
      Behaviors.same

    case Stop(id)=>
      Behaviors.same

    case Start(id)=>
      Behaviors.same
  }
}
