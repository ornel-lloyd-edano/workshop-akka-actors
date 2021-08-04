package io.scalac.auction.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import io.scalac.auction.api.domain.mapping.Implicits._
import io.scalac.auction.api.formats.JsonFormatter
import io.scalac.auction.domain.AuctionStreamService
import io.scalac.auction.domain.api.mapping.Implicits._
import io.scalac.util.Logging
import spray.json._

import scala.util.Try
import akka.http.scaladsl.server.Directives._

trait AuctionServiceWebSocketRoute extends SprayJsonSupport with JsonFormatter with Logging {
  implicit val mat: Materializer
  val auctionService: AuctionStreamService

  def lotPricesFlow: Flow[Message, Message, Any] =
    Flow[Message].collect {
      case tm: TextMessage =>
        Try(tm.getStrictText.parseJson.convertTo[dto.GetLotPrice])
          .fold(err=> {
            logger.error(s"websocket/lot-prices received malformed GetLotPrice message", err)
            None
          }, dto=> Some(dto.toDomain))
      case bm: BinaryMessage =>
        bm.dataStream.runWith(Sink.ignore)
        logger.warn(s"websocket/lot-prices received binary message but was ignored.")
        None
    }.collect {
      case Some(getLotPrice)=> getLotPrice
    }.via(auctionService.lotPricesFlow.map(lotPrices=> TextMessage(lotPrices.map(_.toApi).toJson.prettyPrint)))

  def bidFlow: Flow[Message, Message, Any] =
    Flow[Message].map {
      case tm: TextMessage =>
        Try(tm.getStrictText.parseJson.convertTo[dto.SendBid])
          .fold(err=> {
            logger.error(s"websocket/bid received malformed SendBid message", err)
            None
          }, dto=> Some(dto.toDomain))
      case bm: BinaryMessage =>
        bm.dataStream.runWith(Sink.ignore)
        logger.warn(s"websocket/bid received binary message but was ignored.")
        None
    }.collect {
      case Some(bid)=> bid
    }.via(auctionService.bidsFlow.map(lot=> TextMessage( lot.toApi.toJson.prettyPrint )))

  def webSocketRoute =
    path("websocket" / "lot-prices") {
      handleWebSocketMessages(lotPricesFlow)
    } ~
    path("websocket" / "bid") {
      handleWebSocketMessages(bidFlow)
    }

}
