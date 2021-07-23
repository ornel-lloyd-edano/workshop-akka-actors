package io.scalac.auction.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import io.scalac.auction.domain.AuctionService
import io.scalac.auction.domain.model.ServiceFailure._
import io.scalac.auction.domain.model.{Auction, AuctionId, AuctionStates, Lot, LotId}
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spray.json._

import scala.concurrent.Future

class AuctionServiceControllerSpec extends AnyFlatSpec with Matchers with MockFactory with ScalatestRouteTest  with SprayJsonSupport {

  val mockAuctionService = mock[AuctionService]
  val controller = new AuctionServiceController(mockAuctionService)

  "AuctionServiceController" should "respond with 201 Created along with id of created auction if create auction is successful" in {
    val mockServiceResponse = AuctionId("1")
    (mockAuctionService.createAuction _).expects().returning(Future.successful(Right(mockServiceResponse)))
    Post("/auctions") ~> controller.createAuction ~> check {
      status should be (StatusCodes.Created)
      val expected = s"Auction [${mockServiceResponse.id}] created."
      entityAs[String] should be (expected)
    }
  }

  "AuctionServiceController" should "respond with 500 InternalServerError and error message in plain text if create auction encountered an exception" in {
    val mockServiceResponse = UnexpectedFailure("some exception")
    (mockAuctionService.createAuction _).expects().returning(Future.successful(Left(mockServiceResponse)))
    Post("/auctions") ~> controller.createAuction ~> check {
      status should be (StatusCodes.InternalServerError)
      entityAs[String] should be (mockServiceResponse.message)
    }
  }

  "AuctionServiceController" should "respond with 200 OK and plain text message if starting an auction using an id is successful" in {
    (mockAuctionService.startAuction _).expects("1").returning(Future.successful(Right(())))
    Put("/auctions/1/start") ~> controller.startAuction ~> check {
      status should be (StatusCodes.OK)
      val expected = s"Auction [1] started."
      entityAs[String] should be (expected)
    }
  }

  "AuctionServiceController" should "respond with 404 NotFound and plain text message if starting an auction did not find the id given" in {
    val mockServiceResponse = AuctionNotFound("some auction not found message")
    (mockAuctionService.startAuction _).expects("1").returning(Future.successful(Left(mockServiceResponse)))
    Put("/auctions/1/start") ~> controller.startAuction ~> check {
      status should be (StatusCodes.NotFound)
      entityAs[String] should be (mockServiceResponse.message)
    }
  }

  "AuctionServiceController" should "respond with 500 InternalServerError and plain text error message if starting an auction encountered some exception" in {
    val mockServiceResponse = UnexpectedFailure("some exception message")
    (mockAuctionService.startAuction _).expects("1").returning(Future.successful(Left(mockServiceResponse)))
    Put("/auctions/1/start") ~> controller.startAuction ~> check {
      status should be (StatusCodes.InternalServerError)
      entityAs[String] should be (mockServiceResponse.message)
    }
  }

  "AuctionServiceController" should "respond with 200 OK and plain text message if ending an auction using an id is successful" in {
    (mockAuctionService.endAuction _).expects("1").returning(Future.successful(Right(())))
    Put("/auctions/1/end") ~> controller.endAuction ~> check {
      status should be (StatusCodes.OK)
      val expected = s"Auction [1] ended."
      entityAs[String] should be (expected)
    }
  }

  "AuctionServiceController" should "respond with 404 NotFound and plain text message if ending an auction did not find the id given" in {
    val mockServiceResponse = AuctionNotFound("some auction not found message")
    (mockAuctionService.endAuction _).expects("1").returning(Future.successful(Left(mockServiceResponse)))
    Put("/auctions/1/end") ~> controller.endAuction ~> check {
      status should be (StatusCodes.NotFound)
      entityAs[String] should be (mockServiceResponse.message)
    }
  }

  "AuctionServiceController" should "respond with 500 InternalServerError and plain text error message if ending an auction encountered some exception" in {
    val mockServiceResponse = UnexpectedFailure("some exception message")
    (mockAuctionService.endAuction _).expects("1").returning(Future.successful(Left(mockServiceResponse)))
    Put("/auctions/1/end") ~> controller.endAuction ~> check {
      status should be (StatusCodes.InternalServerError)
      entityAs[String] should be (mockServiceResponse.message)
    }
  }

  //TODO unignore after json marshallers is finished for controller
  "AuctionServiceController" should "respond with 200 OK along with json payload of all auctions if get auctions is successful" ignore {
    val mockServiceResponse = Seq(
      Auction("1", AuctionStates.Closed, Seq()),
      Auction("2", AuctionStates.Closed, Seq()),
      Auction("3", AuctionStates.Started, Seq("1", "2", "3"))
    )
    (mockAuctionService.getAuctions _).expects().returning(Future.successful(Right(mockServiceResponse)))
    Get("/auctions") ~> controller.getAllAuctions ~> check {
      status should be (StatusCodes.OK)
      val expected =
        """
          |[
          | {"id":"1", "status":"Closed", "lotIds":[]},
          | {"id":"2", "status":"Closed", "lotIds":[]},
          | {"id":"3", "status":"Started", "lotIds":["1", "2", "3"]},
          |]
          |""".stripMargin.parseJson
      entityAs[JsValue] should be (expected)
    }
  }

  "AuctionServiceController" should "respond with 500 InternalServerError along with plain text error message if get auctions encountered an exception" in {
    val mockServiceResponse = UnexpectedFailure("some exception")
    (mockAuctionService.getAuctions _).expects().returning(Future.successful(Left(mockServiceResponse)))
    Get("/auctions") ~> controller.getAllAuctions ~> check {
      status should be (StatusCodes.InternalServerError)
      entityAs[String] should be (mockServiceResponse.message)
    }
  }

  //TODO unignore when unmarshalling of json request payload is implemented in the controller
  "AuctionServiceController" should "respond with 201 Created along with id of created lot if create lot is successful" ignore {
    val mockInputs = ("1", Some("mock lot"), Some(BigDecimal(999)))
    val mockServiceResponse = LotId("1")
    (mockAuctionService.addLot (_:String, _:Option[String], _:Option[BigDecimal]))
      .expects(mockInputs._1, mockInputs._2, mockInputs._3).returning(Future.successful(Right(mockServiceResponse)))

    Post(s"/auctions/${mockInputs._1}/lots") ~> controller.createLot ~> check {
      status should be (StatusCodes.Created)
      val expected = s"Lot [${mockServiceResponse.id}] in auction [${mockInputs._1}] is created."
      entityAs[String] should be (expected)
    }
  }

  "AuctionServiceController" should "respond with 404 NotFound along with plain text message if auction id is not found in create lot" in {
    val mockInputs = ("1", None, None)
    val mockServiceResponse = AuctionNotFound("some message")
    (mockAuctionService.addLot (_:String, _:Option[String], _:Option[BigDecimal]))
      .expects(mockInputs._1, mockInputs._2, mockInputs._3).returning(Future.successful(Left(mockServiceResponse)))

    Post(s"/auctions/${mockInputs._1}/lots") ~> controller.createLot ~> check {
      status should be (StatusCodes.NotFound)
      entityAs[String] should be (mockServiceResponse.message)
    }
  }

  "AuctionServiceController" should "respond with 400 BadRequest along with plain text message if auction is not ready to create a lot" in {
    val mockInputs = ("1", None, None)
    val mockServiceResponse = AuctionNotReady("some message")
    (mockAuctionService.addLot (_:String, _:Option[String], _:Option[BigDecimal]))
      .expects(mockInputs._1, mockInputs._2, mockInputs._3).returning(Future.successful(Left(mockServiceResponse)))

    Post(s"/auctions/${mockInputs._1}/lots") ~> controller.createLot ~> check {
      status should be (StatusCodes.BadRequest)
      entityAs[String] should be (mockServiceResponse.message)
    }
  }

  "AuctionServiceController" should "respond with 500 InternalServerError along with plain text error message if create lot encountered some other exception" in {
    val mockInputs = ("1", None, None)
    val mockServiceResponse = UnexpectedFailure("some exception message")
    (mockAuctionService.addLot (_:String, _:Option[String], _:Option[BigDecimal]))
      .expects(mockInputs._1, mockInputs._2, mockInputs._3).returning(Future.successful(Left(mockServiceResponse)))

    Post(s"/auctions/${mockInputs._1}/lots") ~> controller.createLot ~> check {
      status should be (StatusCodes.InternalServerError)
      entityAs[String] should be (mockServiceResponse.message)
    }
  }

  //TODO unignore when response json payload marshalling is implemented in the controller
  "AuctionServiceController" should "respond with 200 OK along with plain text message if bid is successful" ignore {
    val mockInputs = ("1", "1", "user-1", BigDecimal(999), None)
    val mockServiceResponse = Lot(id = "1", auctionId = "1", description = Some("secret box"), topBidder = Some("user-1"), topBid = Some(BigDecimal(999)))
    (mockAuctionService.bid (_:String, _:String, _:String, _:BigDecimal, _:Option[BigDecimal]))
      .expects(mockInputs._1, mockInputs._2, mockInputs._3, mockInputs._4, mockInputs._5).returning(Future.successful(Right(mockServiceResponse)))

    Post(s"/auctions/${mockInputs._1}/lots/${mockInputs._2}") ~> controller.bid ~> check {
      status should be (StatusCodes.OK)
      entityAs[JsValue] should be (mockServiceResponse)
    }
  }

  //TODO unignore when response json payload marshalling is implemented in the controller
  "AuctionServiceController" should "respond with 404 NotFound along with plain text message if bid did not find the auction" ignore {
    val mockInputs = ("1", "1", "user-1", BigDecimal(999), None)
    val mockServiceResponse = AuctionNotFound("some message")
      (mockAuctionService.bid (_:String, _:String, _:String, _:BigDecimal, _:Option[BigDecimal]))
      .expects(mockInputs._1, mockInputs._2, mockInputs._3, mockInputs._4, mockInputs._5).returning(Future.successful(Left(mockServiceResponse)))

    Post(s"/auctions/${mockInputs._1}/lots/${mockInputs._2}") ~> controller.bid ~> check {
      status should be (StatusCodes.NotFound)
      entityAs[String] should be (mockServiceResponse)
    }
  }

  //TODO unignore when response json payload marshalling is implemented in the controller
  "AuctionServiceController" should "respond with 404 NotFound along with plain text message if bid did not find the lot" ignore {
    val mockInputs = ("1", "1", "user-1", BigDecimal(999), None)
    val mockServiceResponse = LotNotFound("some message")
    (mockAuctionService.bid (_:String, _:String, _:String, _:BigDecimal, _:Option[BigDecimal]))
      .expects(mockInputs._1, mockInputs._2, mockInputs._3, mockInputs._4, mockInputs._5).returning(Future.successful(Left(mockServiceResponse)))

    Post(s"/auctions/${mockInputs._1}/lots/${mockInputs._2}") ~> controller.bid ~> check {
      status should be (StatusCodes.NotFound)
      entityAs[String] should be (mockServiceResponse)
    }
  }

  //TODO unignore when response json payload marshalling is implemented in the controller
  "AuctionServiceController" should "respond with 400 BadRequest along with plain text message if auction is not open for bidding yet" ignore {
    val mockInputs = ("1", "1", "user-1", BigDecimal(999), None)
    val mockServiceResponse = AuctionNotReady("some message")
    (mockAuctionService.bid (_:String, _:String, _:String, _:BigDecimal, _:Option[BigDecimal]))
      .expects(mockInputs._1, mockInputs._2, mockInputs._3, mockInputs._4, mockInputs._5).returning(Future.successful(Left(mockServiceResponse)))

    Post(s"/auctions/${mockInputs._1}/lots/${mockInputs._2}") ~> controller.bid ~> check {
      status should be (StatusCodes.BadRequest)
      entityAs[String] should be (mockServiceResponse)
    }
  }

  //TODO unignore when response json payload marshalling is implemented in the controller
  "AuctionServiceController" should "respond with 400 BadRequest along with plain text message if user was not able to top the current best bid" ignore {
    val mockInputs = ("1", "1", "user-1", BigDecimal(999), None)
    val mockServiceResponse = BidRejected("some message")
    (mockAuctionService.bid (_:String, _:String, _:String, _:BigDecimal, _:Option[BigDecimal]))
      .expects(mockInputs._1, mockInputs._2, mockInputs._3, mockInputs._4, mockInputs._5).returning(Future.successful(Left(mockServiceResponse)))

    Post(s"/auctions/${mockInputs._1}/lots/${mockInputs._2}") ~> controller.bid ~> check {
      status should be (StatusCodes.BadRequest)
      entityAs[String] should be (mockServiceResponse)
    }
  }

  //TODO unignore when response json payload marshalling is implemented in the controller
  "AuctionServiceController" should "respond with 500 InternalServerError along with plain text message if bid encountered an exception" ignore {
    val mockInputs = ("1", "1", "user-1", BigDecimal(999), None)
    val mockServiceResponse = UnexpectedFailure("some exception message")
    (mockAuctionService.bid (_:String, _:String, _:String, _:BigDecimal, _:Option[BigDecimal]))
      .expects(mockInputs._1, mockInputs._2, mockInputs._3, mockInputs._4, mockInputs._5).returning(Future.successful(Left(mockServiceResponse)))

    Post(s"/auctions/${mockInputs._1}/lots/${mockInputs._2}") ~> controller.bid ~> check {
      status should be (StatusCodes.InternalServerError)
      entityAs[String] should be (mockServiceResponse)
    }
  }

  //TODO unignore when response json payload marshalling is implemented in the controller
  "AuctionServiceController" should "respond with 200 OK along with a lot as json payload if get lot is successful" ignore {
    val mockInput = ("1", "2")
    val mockServiceResponse = Lot(id = mockInput._2, auctionId = mockInput._1,
      description = Some("secret box contents"), topBidder = Some("lloyd"), topBid = Some(BigDecimal(9999)))
    (mockAuctionService.getLotById (_: String, _: String)).expects(mockInput._1, mockInput._2).returning(Future.successful(Right(mockServiceResponse)))
    Get(s"/auctions/${mockInput._1}/lots/${mockInput._2}") ~> controller.getLotById ~> check {
      status should be (StatusCodes.OK)
      val expected =
        """
          |{
          |"id": "2",
          |"auctionId": "1",
          |"description": "secret box contents",
          |"topBidder": "lloyd",
          |"topBid": 9999
          |}
          |""".stripMargin.parseJson
      entityAs[JsValue] should be (expected)
    }
  }

  "AuctionServiceController" should "respond with 404 NotFound along with a plain text message if get lot did not find the auction" in {
    val mockInput = ("1", "2")
    val mockServiceResponse = AuctionNotFound("some error message")
    (mockAuctionService.getLotById (_: String, _: String)).expects(mockInput._1, mockInput._2).returning(Future.successful(Left(mockServiceResponse)))
    Get(s"/auctions/${mockInput._1}/lots/${mockInput._2}") ~> controller.getLotById ~> check {
      status should be (StatusCodes.NotFound)
      entityAs[String] should be (mockServiceResponse.message)
    }
  }

  "AuctionServiceController" should "respond with 404 NotFound along with a plain text message if get lot was not found" in {
    val mockInput = ("1", "2")
    val mockServiceResponse = LotNotFound("some error message")
    (mockAuctionService.getLotById (_: String, _: String)).expects(mockInput._1, mockInput._2).returning(Future.successful(Left(mockServiceResponse)))
    Get(s"/auctions/${mockInput._1}/lots/${mockInput._2}") ~> controller.getLotById ~> check {
      status should be (StatusCodes.NotFound)
      entityAs[String] should be (mockServiceResponse.message)
    }
  }

  "AuctionServiceController" should "respond with 400 BadRequest along with a plain text message when get lot if auction is already stopped" in {
    val mockInput = ("1", "2")
    val mockServiceResponse = AuctionNotReady("some error message")
    (mockAuctionService.getLotById (_: String, _: String)).expects(mockInput._1, mockInput._2).returning(Future.successful(Left(mockServiceResponse)))
    Get(s"/auctions/${mockInput._1}/lots/${mockInput._2}") ~> controller.getLotById ~> check {
      status should be (StatusCodes.BadRequest)
      entityAs[String] should be (mockServiceResponse.message)
    }
  }

  "AuctionServiceController" should "respond with 500 InternalServerError along with a plain text message when get lot encounter an exception" in {
    val mockInput = ("1", "2")
    val mockServiceResponse = UnexpectedFailure("some exception message")
    (mockAuctionService.getLotById (_: String, _: String)).expects(mockInput._1, mockInput._2).returning(Future.successful(Left(mockServiceResponse)))
    Get(s"/auctions/${mockInput._1}/lots/${mockInput._2}") ~> controller.getLotById ~> check {
      status should be (StatusCodes.InternalServerError)
      entityAs[String] should be (mockServiceResponse.message)
    }
  }

  //TODO unignore when response json payload marshalling is implemented in the controller
  "AuctionServiceController" should "respond with 200 OK along with an array of lots as json payload if get lots by auction is successful" ignore {
    val mockInput = "1"
    val mockServiceResponse = Seq(
      Lot(id = "1", auctionId = mockInput, description = Some("secret box contents"),
        topBidder = Some("lloyd"), topBid = Some(BigDecimal(9999))),
      Lot(id = "2", auctionId = mockInput, description = Some("sealed pirate treasure chest"),
        topBidder = Some("dennis"), topBid = Some(BigDecimal(7777))),
      Lot(id = "3", auctionId = mockInput, description = Some("fossilized plants"),
        topBidder = None, topBid = None)
    )
    (mockAuctionService.getLotsByAuction (_: String)).expects(mockInput).returning(Future.successful(Right(mockServiceResponse)))
    Get(s"/auctions/${mockInput}/lots") ~> controller.getLotsByAuction ~> check {
      status should be (StatusCodes.OK)
      val expected =
        """
          |{
          |"id": "1",
          |"auctionId": "1",
          |"description": "secret box contents",
          |"topBidder": "lloyd",
          |"topBid": 9999
          |},
          |{
          |"id": "2",
          |"auctionId": "1",
          |"description": "sealed pirate treasure chest",
          |"topBidder": "dennis",
          |"topBid": 7777
          |},
          |{
          |"id": "3",
          |"auctionId": "1",
          |"description": "fossilized plants"
          |}
          |""".stripMargin.parseJson
      entityAs[JsValue] should be (expected)
    }
  }

  "AuctionServiceController" should "respond with 404 NotFound along with a plain text message if get all lots by auction did not find the auction" in {
    val mockInput = "1"
    val mockServiceResponse = AuctionNotFound("some error message")
    (mockAuctionService.getLotsByAuction (_: String)).expects(mockInput).returning(Future.successful(Left(mockServiceResponse)))
    Get(s"/auctions/${mockInput}/lots") ~> controller.getLotsByAuction ~> check {
      status should be (StatusCodes.NotFound)
      entityAs[String] should be (mockServiceResponse.message)
    }
  }

  "AuctionServiceController" should "respond with 400 BadRequest along with a plain text message when get all lots by auction but auction is already stopped" in {
    val mockInput = "1"
    val mockServiceResponse = AuctionNotReady("some error message")
    (mockAuctionService.getLotsByAuction (_: String)).expects(mockInput).returning(Future.successful(Left(mockServiceResponse)))
    Get(s"/auctions/${mockInput}/lots") ~> controller.getLotsByAuction ~> check {
      status should be (StatusCodes.BadRequest)
      entityAs[String] should be (mockServiceResponse.message)
    }
  }

  "AuctionServiceController" should "respond with 500 InternalServerError along with a plain text message when get all lots by auction encountered an exception" in {
    val mockInput = "1"
    val mockServiceResponse = UnexpectedFailure("some exception message")
    (mockAuctionService.getLotsByAuction (_: String)).expects(mockInput).returning(Future.successful(Left(mockServiceResponse)))
    Get(s"/auctions/${mockInput}/lots") ~> controller.getLotsByAuction ~> check {
      status should be (StatusCodes.InternalServerError)
      entityAs[String] should be (mockServiceResponse.message)
    }
  }
}
