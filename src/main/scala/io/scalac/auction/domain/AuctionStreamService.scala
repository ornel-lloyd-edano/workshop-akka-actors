package io.scalac.auction.domain

import akka.NotUsed
import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout

import io.scalac.auction.domain.actor.persistent.AuctionActorManager
import io.scalac.auction.domain.model.{BidFail, BidResult, BidSuccess, GetLotPrice, Lot, LotPrice, SendBid}
import io.scalac.util.{ConfigProvider, Logging}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

trait AuctionStreamService extends Logging {
  this: AuctionService=>

  implicit val ec: ExecutionContext
  implicit val mat: Materializer
  implicit val config: ConfigProvider
  implicit val scheduler: Scheduler
  implicit val timeout: Timeout

  lazy val waitDuration: FiniteDuration = (config.getIntConfigVal("streaming.await.duration").getOrElse(3) seconds)

  val auctionManager: ActorRef[AuctionActorManager.AuctionMgmtCommand]

  def getBidsSink = Sink.foreach[SendBid] {
    case SendBid(auctionId, lotId, userId, amount, maxAmount)=>
      bid(auctionId, lotId, userId, amount, maxAmount)
  }

  def getLotPricesSource: Source[LotPrice, Any] = {
    val result = auctionManager.ask[AuctionActorManager.AuctionMgmtResponse](ref=>
      AuctionActorManager.GetStreamSource(ref))
      .map {
        case AuctionActorManager.StreamSource(source) =>
          source.collect {
            case AuctionActorManager.StreamActor.Message(AuctionActorManager.BidAccepted(_, lotId, auctionId, newPrice))=>
              LotPrice(lotId, auctionId, Some(newPrice))
          }
      }
    Await.result(result, waitDuration)
  }

  def bidsFlow: Flow[SendBid, BidResult, NotUsed] = {
    Flow[SendBid].map {
      case sendBid @ SendBid(userId, lotId, auctionId, amount, maxAmount)=>
        val result = bid(auctionId, lotId, userId, amount, maxAmount).map {
          case Right(lot)=>
            BidSuccess(sendBid, lot)
          case Left(fail)=>
            logger.debug(s"AuctionStreamService received failure [${fail.message}]")
            BidFail(sendBid, fail.message)
        }
        Await.result(result, waitDuration)
    }
  }

  def lotPricesFlow: Flow[GetLotPrice, Seq[LotPrice], NotUsed] = {
    Flow[GetLotPrice].map {
      case GetLotPrice(Some(auctionId), Some(lotId)) =>
        logger.debug(s"AuctionStreamService received message GetLot(auctionId = $auctionId, lotId = $lotId)")

        val result = getLotById(auctionId, lotId).map {
          case Right(Lot(lotId, auctionId, _, _, price))=>
            logger.debug(s"AuctionStreamService received response Lot(lotId = $lotId, auctionId = $auctionId, price = $price)")
            Seq(LotPrice(lotId, auctionId, price))
          case Left(fail)=>
            logger.debug(s"AuctionStreamService received failure [${fail.message}]")
            Seq.empty[LotPrice]

        }
        Await.result(result, waitDuration)
      case GetLotPrice(Some(auctionId), None)=>
        val result = getLotsByAuction(auctionId).map {
          case Right(lots)=>
            lots.map(lot=> LotPrice(lot.id, lot.auctionId, lot.topBid))
          case Left(fail)=>
            logger.debug(s"AuctionStreamService received failure [${fail.message}]")
            Seq.empty[LotPrice]
        }
        Await.result(result, waitDuration)
      case GetLotPrice(None, Some(_))=>
        Seq.empty[LotPrice]

      case GetLotPrice(None, None)=>
        val result = getAuctions.flatMap {
          case Right(auctions)=>
            Future.sequence {
              auctions.map(auction=> getLotsByAuction(auction.id).collect {
                case Right(lots)=> lots
              })
            }.map(lots=> lots.flatten.map(lot=> LotPrice(lot.id, lot.auctionId, lot.topBid)))

          case Left(fail)=>
            logger.debug(s"AuctionStreamService received failure [${fail.message}]")
            Future.successful(Seq.empty[LotPrice])
        }
        Await.result(result, waitDuration)
    }
  }

}
