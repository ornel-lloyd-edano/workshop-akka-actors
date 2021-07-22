package io.scalac.auction

import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import io.scalac.auction.model.{Auction, AuctionId, Lot, ServiceFailure}
import io.scalac.util.{ConfigProvider, ExecutionContextProvider}

import scala.concurrent.duration._
import scala.concurrent.Future

class DefaultAuctionService(auctionManager: ActorRef[AuctionActorManager.AuctionMgmtCommand])
                           (implicit ecProvider: ExecutionContextProvider, config: ConfigProvider, scheduler: Scheduler, logger: org.slf4j.Logger) extends AuctionService {

  implicit val ec1 = ecProvider.cpuBoundExCtx
  implicit val timeout = Timeout(config.getIntConfigVal("auction.timeout.seconds").getOrElse(1) seconds)

  override def createAuction: Future[Either[ServiceFailure, AuctionId]] = {
    auctionManager.ask(AuctionActorManager.Create).map {
      case AuctionActorManager.Created(id)=> Right(AuctionId(id))
      case other=>
        logger.error(s"Sent [Create] message to actor but received unexpected [$other] message.")
        Left(ServiceFailure.UnexpectedResponse(s"Creating an auction received unexpected response. See logs for details."))
    }.recover {
      case exception: Throwable=>
        val errorMsg = "Exception in createAuction"
        logger.error(errorMsg, exception)
        Left(ServiceFailure.UnexpectedFailure(s"$errorMsg. See logs for details."))
    }
  }

  override def getAuctions: Future[Either[ServiceFailure, Seq[Auction]]] = ???

  override def addLot(auctionId: String, description: Option[String],
                      minBidAmount: Option[BigDecimal]): Future[Either[ServiceFailure, Lot]] = {
    auctionManager.ask[AuctionActorManager.AuctionMgmtResponse](ref=> AuctionActorManager.AddLot(auctionId, description, minBidAmount, ref)).map {
      case AuctionActorManager.LotAdded(auctionId, lotId)=>
        Right(Lot(lotId, auctionId, description, None, None))
      case AuctionActorManager.AuctionNotFound(auctionId)=>
        Left(ServiceFailure.AuctionNotFound(s"Lot was not added because auction [$auctionId] was not found."))
      case AuctionActorManager.CommandRejected=>
        Left(ServiceFailure.AuctionNotReady(s"Failed to add lot because auction [$auctionId] is not yet started or was already closed."))
      case other=>
        logger.error(s"Sent [AddLot] message to actor but received unexpected [$other] message.")
        Left(ServiceFailure.UnexpectedResponse(s"Adding lot to auction [$auctionId] received unexpected response. See logs for details."))

    }.recover {
      case exception: Throwable=>
        val errorMsg = "Exception in addLot"
        logger.error(s"$errorMsg(auctionId = $auctionId, description = $description, minBidAmount = $minBidAmount)", exception)
        Left(ServiceFailure.UnexpectedFailure(s"$errorMsg. See logs for details"))
    }
  }

  override def startAuction(auctionId: String): Future[Either[ServiceFailure, Unit]] = {
    auctionManager.ask[AuctionActorManager.AuctionMgmtResponse](ref=> AuctionActorManager.Start(auctionId, ref))
      .map {
        case AuctionActorManager.Started(id) if id == auctionId =>
          Right(())
        case AuctionActorManager.AuctionNotFound(auctionId)=>
          Left(ServiceFailure.AuctionNotFound(s"Start auction failed because auction [$auctionId] was not found."))
        case other=>
          logger.error(s"Sent [Start] message to actor but received unexpected [$other] message.")
          Left(ServiceFailure.UnexpectedResponse(s"Start auction [$auctionId] received unexpected response. See logs for details."))
      }.recover {
      case exception: Throwable=>
        val errorMsg = "Exception in startAuction"
        logger.error(s"$errorMsg(auctionId = $auctionId)", exception)
        Left(ServiceFailure.UnexpectedFailure(s"$errorMsg. See logs for details."))
      }
  }

  override def endAuction(auctionId: String): Future[Either[ServiceFailure, Unit]] = {
    auctionManager.ask[AuctionActorManager.AuctionMgmtResponse](ref=>
      AuctionActorManager.Stop(auctionId, ref))
      .map {
        case AuctionActorManager.Stopped(id) if id == auctionId =>
          Right(())
        case AuctionActorManager.AuctionNotFound(auctionId)=>
          Left(ServiceFailure.AuctionNotFound(s"End auction failed because auction [$auctionId] was not found."))
        case other=>
          logger.error(s"Sent [Stop] message to actor but received unexpected [$other] message.")
          Left(ServiceFailure.UnexpectedResponse(s"End auction [$auctionId] received unexpected response. See logs for details."))
      }.recover {
      case exception: Throwable=>
        val errorMsg = "Exception in endAuction"
        logger.error(s"$errorMsg(auctionId = $auctionId)", exception)
        Left(ServiceFailure.UnexpectedFailure(s"$errorMsg. See logs for details."))
    }
  }

  override def getLotById(auctionId: String, lotId: String): Future[Either[ServiceFailure, Lot]] = {
    auctionManager.ask[AuctionActorManager.AuctionMgmtResponse](ref=>
      AuctionActorManager.GetLot(auctionId, lotId, ref))
      .map {
        case AuctionActorManager.LotDetails(auctionId, lotId, description, currentTopBidder, currentBidAmount)=>
          Right(Lot(lotId, auctionId, description, currentTopBidder, currentBidAmount))
        case AuctionActorManager.AuctionNotFound(auctionId)=>
          Left(ServiceFailure.AuctionNotFound(s"Get lot failed because auction [$auctionId] was not found."))
        case AuctionActorManager.LotNotFound(_, lotId)=>
          Left(ServiceFailure.LotNotFound(s"Get lot failed because lot [$lotId] was not found."))
        case AuctionActorManager.CommandRejected=>
          Left(ServiceFailure.AuctionNotReady(s"Get lot [$lotId] failed because auction [$auctionId] is not yet started or was already closed."))
        case other=>
          logger.error(s"Sent [GetLot] message to actor but received unexpected [$other] message.")
          Left(ServiceFailure.UnexpectedResponse(s"Get lot [$lotId] received unexpected response. See logs for details."))
      }.recover {
      case exception: Throwable=>
        val errorMsg = "Exception in getLotById"
        logger.error(s"$errorMsg(auctionId = $auctionId, lotId = $lotId)", exception)
        Left(ServiceFailure.UnexpectedFailure(s"$errorMsg. See logs for details."))
    }
  }

  override def getLotsByAuction(auctionId: String): Future[Either[ServiceFailure, Seq[Lot]]] = {
    auctionManager.ask[AuctionActorManager.AuctionMgmtResponse](ref=>
      AuctionActorManager.GetAllLotsByAuction(auctionId, ref))
      .map {
        case AuctionActorManager.AggregatedLotDetails(lots)=>
          Right(lots.map(lot=> Lot(id = lot.lotId, auctionId = auctionId, description = lot.description,
            topBidder = lot.currentTopBidder, topBid = lot.currentBidAmount)).sortBy(_.id))
        case AuctionActorManager.AuctionNotFound(auctionId)=>
          Left(ServiceFailure.AuctionNotFound(s"Get all lots failed because auction [$auctionId] was not found."))
        case other=>
          logger.error(s"Sent [GetAllLotsByAuction] message to actor but received unexpected [$other] message.")
          Left(ServiceFailure.UnexpectedResponse(s"Get all lots received unexpected response. See logs for details."))
      }.recover {
      case exception: Throwable=>
        val errorMsg = "Exception in getLotsByAuction"
        logger.error(s"$errorMsg(auctionId = $auctionId)", exception)
        Left(ServiceFailure.UnexpectedFailure(s"$errorMsg. See logs for details."))
    }
  }

  override def bid(auctionId: String, lotId: String, userId: String,
                   amount: BigDecimal, maxAmount: Option[BigDecimal]): Future[Either[ServiceFailure, Lot]] = {
    auctionManager.ask[AuctionActorManager.AuctionMgmtResponse](ref=>
      AuctionActorManager.Bid(userId, auctionId, lotId, amount, maxAmount.getOrElse(amount), ref))
      .map {
      case AuctionActorManager.BidAccepted(_, _)=>
        Right(userId)

      case AuctionActorManager.BidRejected(userId, lotId)=>
        Left(ServiceFailure.BidRejected(s"User [$userId] failed to top the current top bidder for lot [$lotId] in auction [$auctionId]."))
      case AuctionActorManager.AuctionNotFound(auctionId)=>
        Left(ServiceFailure.AuctionNotFound(s"Bid did not proceed because auction [$auctionId] was not found."))
      case AuctionActorManager.LotNotFound(_, lotId)=>
        Left(ServiceFailure.LotNotFound(s"Bid did not proceed because lot [$lotId] was not found."))
      case AuctionActorManager.CommandRejected=>
        Left(ServiceFailure.AuctionNotReady(s"User [$userId] failed to bid because auction [$auctionId] is not yet started or was already closed."))
      case other=>
        logger.error(s"Sent [Bid] message to actor but received unexpected [$other] message.")
        Left(ServiceFailure.UnexpectedResponse(s"Bid to lot [$lotId] received unexpected response. See logs for details."))

    }.recover {
      case exception: Throwable=>
        val errorMsg = "Exception in bid"
        logger.error(s"$errorMsg(auctionId = $auctionId, lotId = $lotId, userId = $userId, amount = $amount, maxAmount = $maxAmount)", exception)
        Left(ServiceFailure.UnexpectedFailure(s"$errorMsg. See logs for details."))
    }.flatMap {
      case Right(_)=> getLotById(auctionId, lotId)
      case Left(other)=> Future.successful(Left(other))
    }
  }
}
