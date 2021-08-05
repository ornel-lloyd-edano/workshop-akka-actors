package io.scalac.util.http

import akka.event.Logging.LogLevel
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.{HttpEntity, HttpRequest}
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LogEntry, LoggingMagnet}
import akka.http.scaladsl.server.{Directive0, RouteResult}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink

import scala.concurrent.Await
import scala.concurrent.duration._

trait RouteLogging {
  implicit val mat: Materializer
  val logRequestHeaders: Boolean = false

  def logRequestResponse(level: LogLevel = Logging.InfoLevel): Directive0 = {
    DebuggingDirectives.logRequestResult(LoggingMagnet(requestResponseLogger(_, level)))
  }

  def logInfoRequestResponse: Directive0 = logRequestResponse(Logging.InfoLevel)

  def logDebugRequestResponse: Directive0 = logRequestResponse(Logging.DebugLevel)

  private def entityAsString(entity: HttpEntity): String = {
    val awaitable = entity.dataBytes
      .map(_.decodeString( entity.contentType.charsetOption.map(_.value).getOrElse("") )).runWith(Sink.head)
    Await.result(awaitable, 2 seconds)
  }

  private def requestResponseLogger(loggingAdapter: LoggingAdapter, level: LogLevel)(request: HttpRequest)(response: RouteResult): Unit = {
    val entry = response match {
      case Complete(resp) =>
        val loggingString =
          s"""Request: ${request.method.value} ${request.uri} ${if (logRequestHeaders) "headers: " + request.headers.map(h=> s"${h.name()}: ${h.value()}" )}
             |Response: ${resp.status} ${entityAsString(resp.entity)}
             |""".stripMargin
        LogEntry(loggingString, level)
      case Rejected(reason) =>
        LogEntry(s"Rejected Reason: ${reason.mkString(",")}", level)
    }
    entry.logTo(loggingAdapter)
  }

}
