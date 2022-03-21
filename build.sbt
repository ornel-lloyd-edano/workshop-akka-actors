name := "akka-auction-workshop"

version := "1.0"

scalaVersion := "2.12.6"

val akkaVersion = "2.6.15"
val akkaHttpVersion = "10.2.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.1.4" % Test,
  "org.scalamock" %% "scalamock" % "5.1.0" % Test,

  "ch.qos.logback" % "logback-classic" % "1.1.3" % Runtime
)
