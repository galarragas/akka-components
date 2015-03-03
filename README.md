# akka-components
Collection of AKKA Related Components

[![Build Status](https://api.travis-ci.org/galarragas/akka-components.png)](http://travis-ci.org/galarragas/akka-components)

## Circuit-Breaker Actor

This is an alternative implementation of the [AKKA Circuit Breaker Pattern](http://doc.akka.io/docs/akka/snapshot/common/circuitbreaker.html). 
The main difference is that is intended to be used only for request-reply interactions with actor using the Circuit-Breaker as a proxy of the target one
in order to provide the same failfast functionnalities and a protocol similar to the AKKA Pattern implementation 


### Usage

Let's assume we have an actor wrapping a back-end service and able to respond to `Request` calls with a `Response` object
containing an `Either[String, String]` to map successful and failed responses. The service is also potentially slowing down
because of the workload.

A simple implementation can be given by this class

```scala
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

  import context.dispatcher

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
```

If we want to interface with this service using the Circuit Breaker we can use two approaches:

Using a non-conversational approach:

```scala
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
```

Using the ASK pattern, in this case it is useful to be able to map circuit open failures to the same type of failures
returned by the service (a `Left[String]` in our case):


```scala
class CircuitBreakerAskExample(potentiallyFailingService: ActorRef) extends Actor with ActorLogging {
  import SimpleService._
  import akka.pattern._

  implicit val askTimeout: Timeout = 2.seconds

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

  import context.dispatcher

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
```

## Adding Dependency to AKKA Components

Add conjars repository to your resolvers:

```
resolvers += "ConJars" at "http://conjars.org/repo",
```

then add the following dependencies to your sbt configuration

```
libraryDependencies += "com.pragmasoft" %% "akka-components" % "0.1"
```

## Target AKKA and Scala Versions:

AKKA Components is available for

- Scala 2.10.4 - 2.11.5
- Akka 2.3.2



## License

Copyright 2015 PragmaSoft Ltd.

Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0