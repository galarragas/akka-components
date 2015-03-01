package com.pragmasoft.reactive.akka.components.circuitbreaker

import akka.actor.ActorSystem
import akka.testkit.{TestProbe, TestKit}
import com.pragmasoft.reactive.akka.components.circuitbreaker.CircuitBreakerActor.{CircuitOpenFailure, CircuitBreakerActorBuilder}
import org.scalatest.{FlatSpecLike, FlatSpec}
import scala.concurrent.duration._
import scala.language.postfixOps

class CircuitBreakerActorSpec extends TestKit(ActorSystem("CircuitBreakerActorSpec")) with FlatSpecLike  {

  behavior of "CircuitBreakerActor"

  val baseCircuitBreakerBuilder = 
    CircuitBreakerActorBuilder(
      maxFailures = 2, 
      callTimeout = 200 millis,
      resetTimeout = 1 second,
      failureDetector = { _ == "FAILURE" }
    )

  it should "act as a transparent proxy in case of successful requests-replies - forward to target" in {

    val sender = TestProbe()
    val receiver = TestProbe()

    val circuitBreaker = system.actorOf(baseCircuitBreakerBuilder.propsForTarget(receiver.ref))

    sender.send(circuitBreaker, "test message")

    receiver.expectMsg("test message")
  }

  it should "act as a transparent proxy in case of successful requests-replies - full cycle" in {

    val sender = TestProbe()
    val receiver = TestProbe()
    val circuitBreaker = system.actorOf(baseCircuitBreakerBuilder.propsForTarget(receiver.ref))

    sender.send(circuitBreaker, "test message")

    receiver.expectMsg("test message")
    receiver.reply("response")

    sender.expectMsg("response")
  }

  it should "forward further messages before receiving the response of the first one" in {
    val sender = TestProbe()
    val receiver = TestProbe()
    val circuitBreaker = system.actorOf(baseCircuitBreakerBuilder.propsForTarget(receiver.ref))

    sender.send(circuitBreaker, "test message1")
    sender.send(circuitBreaker, "test message2")
    sender.send(circuitBreaker, "test message3")

    receiver.expectMsg("test message1")
    receiver.expectMsg("test message2")
    receiver.expectMsg("test message3")
  }

  it should "send responses to the right sender" in {
    val sender1 = TestProbe()
    val sender2 = TestProbe()
    val receiver = TestProbe()
    val circuitBreaker = system.actorOf(baseCircuitBreakerBuilder.propsForTarget(receiver.ref))

    sender1.send(circuitBreaker, "test message1")
    sender2.send(circuitBreaker, "test message2")

    receiver.expectMsg("test message1")
    receiver.reply("response1")

    receiver.expectMsg("test message2")
    receiver.reply("response2")

    sender1.expectMsg("response1")
    sender2.expectMsg("response2")
  }

  it should "return failed responses too" in {
    val sender = TestProbe()
    val receiver = TestProbe()
    val circuitBreaker = system.actorOf(baseCircuitBreakerBuilder.propsForTarget(receiver.ref))

    sender.send(circuitBreaker, "request")

    receiver.expectMsg("request")
    receiver.reply("FAILURE")

    sender.expectMsg("FAILURE")
  }

  it should "enter open state after reaching the threshold of failed responses" in {
    val sender = TestProbe()
    val receiver = TestProbe()
    val circuitBreaker = system.actorOf(baseCircuitBreakerBuilder.propsForTarget(receiver.ref))
    
    sender.send(circuitBreaker, "request1")
    receiver.expectMsg("request1")
    receiver.reply("FAILURE")
    sender.expectMsg("FAILURE")

    sender.send(circuitBreaker, "request2")
    receiver.expectMsg("request2")
    receiver.reply("FAILURE")
    sender.expectMsg("FAILURE")

    // Have to wait a bit to let the circuit breaker receive the self notification message
    Thread.sleep(300)

    sender.send(circuitBreaker, "request in open state")
    receiver.expectNoMsg
  }

  it should "return a CircuitOpenFailure message when in open state " in {
    val sender = TestProbe()
    val receiver = TestProbe()
    val circuitBreaker = system.actorOf(baseCircuitBreakerBuilder.propsForTarget(receiver.ref))

    sender.send(circuitBreaker, "request1")
    receiver.expectMsg("request1")
    receiver.reply("FAILURE")
    sender.expectMsg("FAILURE")

    sender.send(circuitBreaker, "request2")
    receiver.expectMsg("request2")
    receiver.reply("FAILURE")
    sender.expectMsg("FAILURE")

    // Have to wait a bit to let the circuit breaker receive the self notification message
    Thread.sleep(300)

    sender.send(circuitBreaker, "request in open state")
    sender.expectMsg(CircuitOpenFailure("request in open state"))
  }

  it should "return the converted CircuitOpenFailure if a converter is provided" in {
    val sender = TestProbe()
    val receiver = TestProbe()
    val circuitBreaker = system.actorOf(
      baseCircuitBreakerBuilder
        .copy( openCircuitFailureConverter = { failureMsg => s"NOT SENT: ${failureMsg.failedMsg}"  } )
        .propsForTarget(receiver.ref)
    )

    sender.send(circuitBreaker, "request1")
    receiver.expectMsg("request1")
    receiver.reply("FAILURE")
    sender.expectMsg("FAILURE")

    sender.send(circuitBreaker, "request2")
    receiver.expectMsg("request2")
    receiver.reply("FAILURE")
    sender.expectMsg("FAILURE")

    // Have to wait a bit to let the circuit breaker receive the self notification message
    Thread.sleep(300)

    sender.send(circuitBreaker, "request in open state")
    sender.expectMsg("NOT SENT: request in open state")
  }

  it should "enter open state after reaching the threshold of timed-out responses" in {
    val sender = TestProbe()
    val receiver = TestProbe()
    val circuitBreaker = system.actorOf(baseCircuitBreakerBuilder.propsForTarget(receiver.ref))

    sender.send(circuitBreaker, "request1")

    sender.send(circuitBreaker, "request2")

    Thread.sleep(baseCircuitBreakerBuilder.callTimeout.duration.toMillis + 100)

    receiver.expectMsg("request1")
    receiver.reply("this should be timed out 1")

    receiver.expectMsg("request2")
    receiver.reply("this should be timed out 2")

    // Have to wait a bit to let the circuit breaker receive the self notification message
    Thread.sleep(300)

    sender.send(circuitBreaker, "request in open state")
    receiver.expectNoMsg
  }

}
