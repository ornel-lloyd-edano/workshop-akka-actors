# workshop-akka-actors

This assignment is meant to help you start with Akka in general and Actors in particular.

## Objective overview

You will build an application that will handle bidding on auction items (called lots). Auctions and Lots will be represented as Actors. In the next steps you will add Akka Persistence to preserve actor's state and Akka HTTP to build an API.

## Domain Overview

For this application we'll implement only limited set of features that would otherwise be needed for a full-fledged auction app. The focus should be on technology.

### General rules
- There can be multiple auctions with multiple items.
- The auction item is called a “lot”.
- One lot can be in only one auction.
- The auction can be started and ended but not restarted.
- To be able to bid on lots, the auction has to be started.
- checkout master as your master branch (e.g. jan-kowalski) and branch out from your master branch and merge to it (jan-kowalski -> jan-kowalski-part1 -> jan-kowalski)

### Bidding
- The new bid has to be greater than the current bid.
- User can set a max bid value. The system should then use the lowest possible value to set as the current winning bid, but also remember the max value, so that when next time another user makes a bid it will be automatically resolved against the max bids.
- For this assignment Users are just Ids.

## Part 1

1. Create LotActor representing a lot, that will implement the bidding logic from the Domain Overview.
2. Create AuctionActor representing an auction. Should be implemented as FSM. There are three possible states: Closed, InProgress and Finished. Should support adding and removing lots but only when Closed. Should allow bidding only when InProgress.

Use Akka Typed
### Additional tasks:

- Implement AuctionActor as a [router](https://doc.akka.io/docs/akka/current/routing.html#how-routing-is-designed-within-akka)

## Part 2

Implement following endpoints using Akka Http:

#### Auction
- Create auction
- Start auction
- End auction
- Get all auctions

#### Lot
- Create lot
- Bid
- Get lots by auction
- Get lot by id

### Additional tasks

- Add authentication using the `Bearer` schema (meaning that the request will include a header of format “Authorization : Bearer <token>” for authentication). Based on the token the application will then look for a user in a hardcoded store. Hint: OAuth2 is using the Bearer tokens and Akka Http supports it with special authentication directives.
- [requires Akka Streams] Add websocket endpoint for observing current lot price (optional: make it bidirectional with bidding).

## Part 3

Make actors' state persistent using Akka Persistence. You can use any datastore. Hint: the actors should preserve the state between the application restarts, so the real problem to solve here is how to recreate a dynamic actor tree when the application starts.

### Additional tasks

//TODO
