package com.pragmasoft.reactive.akka.components.circuitbreaker

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestProbe, TestKit}
import com.pragmasoft.reactive.akka.components.circuitbreaker.CircuitBreakerActor._
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

  it should "enter open state after reaching the threshold of failed responses" in new CircuitBreakerScenario {
    // GIVEN
    val circuitBreaker = defaultCircuitBreaker

    (1 to baseCircuitBreakerBuilder.maxFailures) foreach { index =>
      receiverRespondsWithFailureToRequest( s"request$index" )
    }

    waitForCircuitBreakerToReceiveSelfNotificationMessage

    sender.send(circuitBreaker, "request in open state")
    receiver.expectNoMsg
  }

  it should "respond with a CircuitOpenFailure message when in open state " in new CircuitBreakerScenario {
    // GIVEN
    val circuitBreaker = defaultCircuitBreaker

    (1 to baseCircuitBreakerBuilder.maxFailures) foreach { index =>
      receiverRespondsWithFailureToRequest( s"request$index" )
    }

    waitForCircuitBreakerToReceiveSelfNotificationMessage

    sender.send(circuitBreaker, "request in open state")
    sender.expectMsg(CircuitOpenFailure("request in open state"))
  }

  it should "respond with the converted CircuitOpenFailure if a converter is provided" in new CircuitBreakerScenario  {
    val circuitBreaker = system.actorOf(
      baseCircuitBreakerBuilder
        .copy( openCircuitFailureConverter = { failureMsg => s"NOT SENT: ${failureMsg.failedMsg}"  } )
        .propsForTarget(receiver.ref)
    )

    (1 to baseCircuitBreakerBuilder.maxFailures) foreach { index =>
      receiverRespondsWithFailureToRequest( s"request$index" )
    }

    waitForCircuitBreakerToReceiveSelfNotificationMessage

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

  it should "enter HALF OPEN state after the given state timeout, sending the first message only" in new CircuitBreakerScenario {
    // GIVEN
    val circuitBreaker = defaultCircuitBreaker

    // WHEN - ENTERING OPEN STATE
    receiverRespondsWithFailureToRequest("request1")
    receiverRespondsWithFailureToRequest("request2")

    waitForCircuitBreakerToReceiveSelfNotificationMessage

    // THEN
    messageIsRejectedWithOpenCircuitNotification("IGNORED SINCE IN OPEN STATE1")
    messageIsRejectedWithOpenCircuitNotification("IGNORED SINCE IN OPEN STATE2")

    // WHEN - ENTERING HALF OPEN STATE
    waitForResetTimeoutToExpire

    // THEN
    sender.send(circuitBreaker, "First message in half-open state, should be forwarded")
    sender.send(circuitBreaker, "Second message in half-open state, should be ignored")

    receiver.expectMsg("First message in half-open state, should be forwarded")
    receiver.expectNoMsg()

    sender.expectMsg(CircuitOpenFailure("Second message in half-open state, should be ignored"))

  }

  it should "return to CLOSED state from HALF-OPEN if a successful message response notification is received" in new CircuitBreakerScenario {
    // GIVEN
    val circuitBreaker = defaultCircuitBreaker

    // WHEN - Entering HALF OPEN state
    receiverRespondsWithFailureToRequest("request1")
    receiverRespondsWithFailureToRequest("request2")

    waitForResetTimeoutToExpire

    // WHEN - Receiving a successful response
    receiverRespondsToRequestWith("First message in half-open state, should be forwarded", "This should close the circuit")

    waitForCircuitBreakerToReceiveSelfNotificationMessage

    // THEN
    sender.send(circuitBreaker, "request1")
    receiver.expectMsg("request1")

    sender.send(circuitBreaker, "request2")
    receiver.expectMsg("request2")

  }

  it should "return to OPEN state from HALF-OPEN if a FAILURE message response is received" in new CircuitBreakerScenario {
    // GIVEN
    val circuitBreaker = defaultCircuitBreaker

    // WHEN - Entering HALF OPEN state
    receiverRespondsWithFailureToRequest("request1")
    receiverRespondsWithFailureToRequest("request2")

    waitForResetTimeoutToExpire

    // Failure message in HALF OPEN State
    receiverRespondsWithFailureToRequest("First message in half-open state, should be forwarded")

    waitForCircuitBreakerToReceiveSelfNotificationMessage

    // THEN
    sender.send(circuitBreaker, "this should be ignored")
    receiver.expectNoMsg()
    sender.expectMsg(CircuitOpenFailure("this should be ignored"))

  }

  it should "Notify an event status change listener when changing state" in new CircuitBreakerScenario {
    // GIVEN
    override val circuitBreaker = system.actorOf(
      baseCircuitBreakerBuilder
        .copy( circuitEventListener = Some(eventListener.ref) )
        .propsForTarget(receiver.ref)
    )

    // WHEN - Entering OPEN state
    receiverRespondsWithFailureToRequest("request1")
    receiverRespondsWithFailureToRequest("request2")

    waitForCircuitBreakerToReceiveSelfNotificationMessage

    // THEN
    eventListener.expectMsg(CircuitOpen(circuitBreaker))

    // WHEN - Entering HALF OPEN state
    waitForResetTimeoutToExpire

    // THEN
    eventListener.expectMsg(CircuitHalfOpen(circuitBreaker))

    // WHEN - Entering CLOSED state
    receiverRespondsToRequestWith("First message in half-open state, should be forwarded", "This should close the circuit")

    waitForCircuitBreakerToReceiveSelfNotificationMessage

    // THEN
    eventListener.expectMsg(CircuitClosed(circuitBreaker))

  }
  
  trait CircuitBreakerScenario {
    val sender = TestProbe()
    val eventListener = TestProbe()
    val receiver = TestProbe()
    
    def circuitBreaker: ActorRef
    
    def defaultCircuitBreaker = system.actorOf(baseCircuitBreakerBuilder.propsForTarget(receiver.ref))
    
    def receiverRespondsWithFailureToRequest(request: Any) = {
      sender.send(circuitBreaker, request)
      receiver.expectMsg(request)
      receiver.reply("FAILURE")
      sender.expectMsg("FAILURE")
    }  
    
    def receiverRespondsToRequestWith(request: Any, reply: Any) = {
      sender.send(circuitBreaker, request)
      receiver.expectMsg(request)
      receiver.reply(reply)
      sender.expectMsg(reply)  
    }
    
    def waitForCircuitBreakerToReceiveSelfNotificationMessage = Thread.sleep(baseCircuitBreakerBuilder.resetTimeout.duration.toMillis/4)
    
    def waitForResetTimeoutToExpire = Thread.sleep(baseCircuitBreakerBuilder.resetTimeout.duration.toMillis + 100)

    def messageIsRejectedWithOpenCircuitNotification(message: Any) = {
      sender.send(circuitBreaker, message)
      sender.expectMsg(CircuitOpenFailure(message))
    }
    
  }

  
  
}
