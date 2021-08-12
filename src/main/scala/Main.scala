import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import io.scalac.auction.api.AuctionServiceController
import io.scalac.auction.domain.actor.persistent.AuctionActorManager
import io.scalac.auction.domain.DefaultAuctionService
import io.scalac.auth.UsersFromConfigService
import io.scalac.util.{Configs, ExecutionContexts}

object Main extends App {
  implicit val ecProvider = ExecutionContexts
  implicit val ec = ecProvider.cpuBoundExCtx
  implicit val configProvider = Configs
  implicit val system = ActorSystem(AuctionActorManager("1"), "system")
  implicit val mat = Materializer(system)
  implicit val scheduler = system.scheduler
  val auctionMgrActor = system.systemActorOf(AuctionActorManager("1"), "AuctionManagerActor")
  val auctionService = new DefaultAuctionService(auctionMgrActor)
  val userService = new UsersFromConfigService(configProvider)
  val controller = new AuctionServiceController(auctionService, userService)
  Http().newServerAt("localhost", 8080).bind(controller.authenticatedRoutes ~ controller.webSocketRoute)
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}