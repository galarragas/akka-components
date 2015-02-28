# akka-components
Collection of AKKA Related Components

## Circuit-Breaker Actor

This is an alternative implementation of the [AKKA Circuit Breaker Pattern](http://doc.akka.io/docs/akka/snapshot/common/circuitbreaker.html). 
The main difference is that is intended to be used only for request-reply interactions with actor using the Circuit-Breaker as a proxy of the target one
in order to provide the same failfast functionnalities and a protocol similar to the AKKA Pattern implementation 
