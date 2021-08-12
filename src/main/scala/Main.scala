import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import io.scalac.auction.api.AuctionServiceController
import io.scalac.auction.domain.{AuctionActorManager, DefaultAuctionService}
import io.scalac.util.{Configs, ExecutionContexts}

object Main extends App {
  implicit val system = ActorSystem(Behaviors.empty, "system")
  implicit val ecProvider = ExecutionContexts
  implicit val config = Configs
  implicit val scheduler = system.scheduler
  val auctionActorMgr = system.systemActorOf(AuctionActorManager(), "AuctionActorMgr")
  val auctionService = new DefaultAuctionService(auctionActorMgr)
  val controller = new AuctionServiceController(auctionService)

  Http().newServerAt("localhost", 8080).bind(
    controller.createAuctionRoute ~
    controller.startAuctionRoute ~
    controller.endAuctionRoute ~
    controller.getAllAuctions ~
    controller.createLot ~
    controller.bid ~
    controller.getLotByIdRoute ~
    controller.getLotsByAuctionRoute)
    .flatMap(_.unbind())(ecProvider.cpuBoundExCtx)
    .onComplete(_=> system.terminate())(ecProvider.cpuBoundExCtx)

}
