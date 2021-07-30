package io.scalac.auction.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import io.scalac.auction.api.formats.JsonFormatter
import io.scalac.auction.domain.AuctionStreamService
import io.scalac.auction.domain.api.mapping.Implicits._
import io.scalac.auction.domain.model.GetLotPrice
import io.scalac.util.{Logging}

import spray.json._

import scala.util.{Failure, Success, Try}
import akka.http.scaladsl.server.Directives._
import io.scalac.auction.api.dto.SendBid

trait AuctionServiceWebSocketRoute extends SprayJsonSupport with JsonFormatter with Logging {
  implicit val mat: Materializer
  val auctionService: AuctionStreamService

  def lotPricesFlow: Flow[Message, Message, Any] =
    Flow[Message].map {
      case tm: TextMessage =>
          Try(tm.getStrictText.parseJson.convertTo[dto.GetLotPrice]) match {
            case Success(value)=>
              GetLotPrice(Some(value.auctionId), Some(value.lotId))
            case Failure(exception)=>
              logger.error(s"websocket/lot-prices received malformed GetLotPrice message", exception)
              GetLotPrice(None, None)
          }
      case bm: BinaryMessage =>
        // ignore binary messages but drain content to avoid the stream being clogged
        bm.dataStream.runWith(Sink.ignore)
        GetLotPrice(None, None)
    }.via(auctionService.streamLotPrices.map(lotPrices=> TextMessage(lotPrices.map(_.toApi).toJson.prettyPrint)))

  def webSocketRoute =
    path("websocket" / "lot-prices") {
      handleWebSocketMessages(lotPricesFlow)
    }

}
