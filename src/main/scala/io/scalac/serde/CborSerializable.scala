package io.scalac.serde

//marker trait for all case class and case objects participating in actor persistence
//binds to Jackson CBOR (COnside Binary Object Representation) make sure to bind it in application.conf akka.actor.serialization-bindings
trait CborSerializable {

}
