package io.scalac.auction.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import io.scalac.auction.api.auth.AuctionApiJWTClaimValidator
import io.scalac.auction.api.dto.{AddLot, UserBid}
import io.scalac.auction.api.formats.JsonFormatter
import io.scalac.auction.domain.{AuctionService, AuctionStreamService}
import io.scalac.auction.domain.model.ServiceFailure
import io.scalac.auth.UserService
import io.scalac.auction.domain.api.mapping.Implicits._
import io.scalac.util.{ConfigProvider}
import io.scalac.util.http.PayloadConverter

import scala.util.{Failure, Success}

class AuctionServiceController(val auctionService: AuctionService with AuctionStreamService,
                               val userService: UserService)
                              (implicit val config: ConfigProvider, val mat: Materializer)
  extends SprayJsonSupport with JsonFormatter with PayloadConverter
    with AuctionApiJWTClaimValidator with AuctionServiceWebSocketRoute {

  override def getSecret: Option[String] = config.getStringConfigVal("application.secret")

  override def authenticatedRoutes: Route = extractJWT(validateUserClaim) { _ =>
    createAuction ~ startAuction ~ endAuction ~ getAllAuctions ~ createLot ~ bid ~ getLotById ~ getLotsByAuction
  }

  def createAuction: Route = path("auctions") {
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

  //Note: this route is more like RPC-style than Restful style
  def startAuction: Route = path("auctions" / Segment / "start") { auctionId=>
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

  //Note: this route is more like RPC-style than Restful style
  def endAuction: Route = path("auctions" / Segment / "end") { auctionId=>
    put {
      onComplete(auctionService.endAuction(auctionId)) {
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
          complete(StatusCodes.OK, auctions.map(_.toApi).toJsonHttpEntity)
        case Success(Left(failure))=>
          complete(StatusCodes.InternalServerError, failure.message)
        case Failure(exception)=>
          complete(StatusCodes.InternalServerError, exception.getMessage)
      }
    }
  }

  def createLot: Route = path("auctions" / Segment / "lots") { auctionId=>
    post {
      entity(as[AddLot]) { addLot=>
        onComplete(auctionService.addLot(auctionId, addLot.description, addLot.minBidAmount)) {
          case Success(Right(lot))=>
            complete(StatusCodes.Created, s"Lot [${lot}] in auction [$auctionId] is created.")
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

  def bid: Route = path("auctions" / Segment / "lots" / Segment) { (auctionId, lotId)=>
    post {
      entity(as[UserBid]) { userBid=>
        val serviceCall = auctionService.bid(auctionId, lotId, userId = userBid.userId,
          amount = userBid.amount, maxAmount = userBid.maxBid)
        onComplete(serviceCall) {
          case Success(Right(lot))=>
            complete(StatusCodes.OK, s"Bid to lot [${lot.id}] in auction [$auctionId] is successful.")
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
  }

  def getLotById: Route = path("auctions" / Segment / "lots" / Segment) { (auctionId, lotId) =>
    get {
      onComplete(auctionService.getLotById(auctionId, lotId)) {
        case Success(Right(lot))=>
          complete(StatusCodes.OK, lot.toApi.toJsonHttpEntity)
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

  def getLotsByAuction: Route = path("auctions" / Segment / "lots") { auctionId =>
    get {
      onComplete(auctionService.getLotsByAuction(auctionId)) {
        case Success(Right(lots))=>
          complete(StatusCodes.OK,  lots.map(_.toApi).toJsonHttpEntity )
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
