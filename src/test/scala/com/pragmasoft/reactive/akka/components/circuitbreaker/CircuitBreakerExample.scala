package com.pragmasoft.reactive.akka.components.circuitbreaker

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.pragmasoft.reactive.akka.components.circuitbreaker.CircuitBreakerActor.{CircuitOpenFailure, CircuitBreakerActorBuilder}
import com.pragmasoft.reactive.akka.components.circuitbreaker.CircuitBreakerExample.AskFor
import scala.concurrent.duration._
import scala.util.{Success, Failure, Random}

object CircuitBreakerExample {
  case class AskFor(what: String)

}

class CircuitBreakerExample(potentiallyFailingService: ActorRef) extends Actor with ActorLogging {
  import SimpleService._

  val serviceCircuitBreaker =
    context.actorOf(
      CircuitBreakerActorBuilder( maxFailures = 3, callTimeout = 2.seconds, resetTimeout = 30.seconds )
        .copy(
          failureDetector = {
            _ match  {
              case Response(Left(_)) => true
              case _ => false
            }
          }
        )
        .propsForTarget(potentiallyFailingService),
      "serviceCircuitBreaker"
    )

  override def receive: Receive = {
    case AskFor(requestToForward) =>
      serviceCircuitBreaker ! Request(requestToForward)

    case Right(Response(content)) =>
      //handle response
      log.info("Got successful response {}", content)

    case Response(Right(content)) =>
      //handle response
      log.info("Got successful response {}", content)

    case Response(Left(content)) =>
      //handle response
      log.info("Got failed response {}", content)

    case CircuitOpenFailure(failedMsg) =>
      log.warning("Unable to send message {}", failedMsg)
  }
}

class CircuitBreakerAskExample(potentiallyFailingService: ActorRef) extends Actor with ActorLogging {
  import SimpleService._
  import akka.pattern._

  implicit val askTimeout = 2.seconds

  val serviceCircuitBreaker =
    context.actorOf(
      CircuitBreakerActorBuilder( maxFailures = 3, callTimeout = askTimeout, resetTimeout = 30.seconds )
        .copy(
          failureDetector = {
            _ match  {
              case Response(Left(_)) => true
              case _ => false
            }
          }
        )
        .copy(
          openCircuitFailureConverter = { failure =>
            Left(s"Circuit open when processing ${failure.failedMsg}")
          }
        )
        .propsForTarget(potentiallyFailingService),
      "serviceCircuitBreaker"
    )

  override def receive: Receive = {
    case AskFor(requestToForward) =>
      (serviceCircuitBreaker ? Request(requestToForward)).mapTo[Either[String, String]].onComplete {
        case Success(Right(successResponse)) =>
          //handle response
          log.info("Got successful response {}", successResponse)

        case Success(Left(failureResponse)) =>
          //handle response
          log.info("Got successful response {}", failureResponse)

        case Failure(exception) =>
          //handle response
          log.info("Got successful response {}", exception)

      }

  }
}

object SimpleService {
  case class Request(content: String)
  case class Response(content: Either[String, String])
  case object ResetCount
}


/**
 * This is a simple actor simulating a service
 * - Becoming slower with the increase of frequency of input requests
 * - Failing around 30% of the requests
 */
class SimpleService extends Actor with ActorLogging {
  import SimpleService._
  
  var messageCount = 0
  
  context.system.scheduler.schedule(1.second, 1.second, self, ResetCount)
  
  override def receive = {
    case ResetCount =>
      messageCount = 0
      
    case Request(content) =>
      messageCount += 1
      // simulate workload
      Thread.sleep( 100 * messageCount )
      // Fails around 30% of the times
      if(Random.nextInt(100) < 70 ) {
        sender ! Response(Right(s"Successfully processed $content"))
      } else {
        sender ! Response(Left(s"Failure processing $content"))
      }
      
  }
}