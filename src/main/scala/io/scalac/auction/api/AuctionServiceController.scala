package io.scalac.auction.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import io.scalac.auction.domain.AuctionService
import io.scalac.auction.domain.model.ServiceFailure

import scala.util.{Failure, Success}

class AuctionServiceController(auctionService: AuctionService) extends Directives with SprayJsonSupport {

  def createAuctionRoute: Route = path("auctions") {
    post {
      onComplete(auctionService.createAuction) {
        case Success(Right(auctionId))=>
          complete(StatusCodes.Created, s"Auction [$auctionId] created.")
        case Success(Left(failure))=>
          complete(StatusCodes.InternalServerError, failure.message)
        case Failure(exception)=>
          complete(StatusCodes.InternalServerError, exception.getMessage)
      }
    }
  }

  def startAuctionRoute: Route = path("auctions" / Segment / "start") { auctionId=>
      put {
        onComplete(auctionService.startAuction(auctionId)) {
          case Success(Right(_))=>
            complete(StatusCodes.OK, s"Auction [$auctionId] started.")
          case Success(Left(ServiceFailure.AuctionNotFound(message)))=>
            complete(StatusCodes.NotFound, message)
          case Success(Left(ServiceFailure.AuctionNotReady(message)))=>
            complete(StatusCodes.BadRequest, message)
          case Success(Left(otherFailure))=>
            complete(StatusCodes.InternalServerError, otherFailure.message)
          case Failure(exception)=>
            complete(StatusCodes.InternalServerError, exception.getMessage)
        }
      }
  }

    def endAuctionRoute: Route = path("auctions" / Segment / "end") { auctionId=>
      put {
        onComplete(auctionService.startAuction(auctionId)) {
          case Success(Right(_))=>
            complete(StatusCodes.OK, s"Auction [$auctionId] ended.")
          case Success(Left(ServiceFailure.AuctionNotFound(message)))=>
            complete(StatusCodes.NotFound, message)
          case Success(Left(otherFailure))=>
            complete(StatusCodes.InternalServerError, otherFailure.message)
          case Failure(exception)=>
            complete(StatusCodes.InternalServerError, exception.getMessage)
        }
      }
    }

  def getAllAuctions: Route = path("auctions") {
    get {
      onComplete(auctionService.getAuctions) {
        case Success(Right(auctions))=>
          //TODO create marshaller for auctions and include it in response
          complete(StatusCodes.OK)
        case Success(Left(failure))=>
          complete(StatusCodes.InternalServerError, failure.message)
        case Failure(exception)=>
          complete(StatusCodes.InternalServerError, exception.getMessage)
      }
    }
  }

  def createLot: Route = path("auctions" / Segment / "lots") { auctionId=>
    post {
      onComplete(auctionService.addLot(auctionId, None, None)) {
        case Success(Right(lot))=>
          complete(StatusCodes.Created, s"Lot [${lot}] is created.")
        case Success(Left(ServiceFailure.AuctionNotFound(message)))=>
          complete(StatusCodes.NotFound, message)
        case Success(Left(ServiceFailure.AuctionNotReady(message)))=>
          complete(StatusCodes.BadRequest, message)
        case Success(Left(otherFailure))=>
          complete(StatusCodes.InternalServerError, otherFailure.message)
        case Failure(exception)=>
          complete(StatusCodes.InternalServerError, exception.getMessage)
      }
    }
  }

  def bid: Route = path("auctions" / Segment / "lots" / Segment) { (auctionId, lotId)=>
    post {
      //TODO extract userId, amount, and maxAmount from request payload json
      onComplete(auctionService.bid(auctionId, lotId, "userId", BigDecimal(0), Some(BigDecimal(0)))) {
        case Success(Right(lot))=>
          complete(StatusCodes.Created, s"Bid to lot [${lot}] is successful.")
        case Success(Left(ServiceFailure.AuctionNotFound(message)))=>
          complete(StatusCodes.NotFound, message)
        case Success(Left(ServiceFailure.LotNotFound(message)))=>
          complete(StatusCodes.NotFound, message)
        case Success(Left(ServiceFailure.AuctionNotReady(message)))=>
          complete(StatusCodes.BadRequest, message)
        case Success(Left(ServiceFailure.BidRejected(message)))=>
          complete(StatusCodes.BadRequest, message)
        case Success(Left(otherFailure))=>
          complete(StatusCodes.InternalServerError, otherFailure.message)
        case Failure(exception)=>
          complete(StatusCodes.InternalServerError, exception.getMessage)
      }
    }
  }

  def getLotByIdRoute: Route = path("auctions" / Segment / "lots" / Segment) { (auctionId, lotId) =>
    get {
      onComplete(auctionService.getLotById(auctionId, lotId)) {
        case Success(Right(lot))=>
          //TODO create marshaller for Lot
          complete(StatusCodes.OK)
        case Success(Left(ServiceFailure.AuctionNotFound(message)))=>
          complete(StatusCodes.NotFound, message)
        case Success(Left(ServiceFailure.LotNotFound(message)))=>
          complete(StatusCodes.NotFound, message)
        case Success(Left(ServiceFailure.AuctionNotReady(message)))=>
          complete(StatusCodes.BadRequest, message)
        case Success(Left(otherFailure))=>
          complete(StatusCodes.InternalServerError, otherFailure.message)
        case Failure(exception)=>
          complete(StatusCodes.InternalServerError, exception.getMessage)
      }
    }
  }

  def getLotsByAuctionRoute: Route = path("auctions" / Segment / "lots") { auctionId =>
    get {
      onComplete(auctionService.getLotsByAuction(auctionId)) {
        case Success(Right(lots))=>
          //TODO create marshaller for Lot
          complete(StatusCodes.OK)
        case Success(Left(ServiceFailure.AuctionNotFound(message)))=>
          complete(StatusCodes.NotFound, message)
        case Success(Left(ServiceFailure.AuctionNotReady(message)))=>
          complete(StatusCodes.BadRequest, message)
        case Success(Left(otherFailure))=>
          complete(StatusCodes.InternalServerError, otherFailure.message)
        case Failure(exception)=>
          complete(StatusCodes.InternalServerError, exception.getMessage)
      }
    }
  }

}
