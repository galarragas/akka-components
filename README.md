# akka-components
Collection of AKKA Related Components

[![Build Status](https://api.travis-ci.org/galarragas/akka-components.png)](http://travis-ci.org/galarragas/akka-components)

## Circuit-Breaker Actor

This is an alternative implementation of the [AKKA Circuit Breaker Pattern](http://doc.akka.io/docs/akka/snapshot/common/circuitbreaker.html). 
The main difference is that is intended to be used only for request-reply interactions with actor using the Circuit-Breaker as a proxy of the target one
in order to provide the same failfast functionnalities and a protocol similar to the AKKA Pattern implementation 

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