# Daisy 🌼

Daisy is a coroutine based message processing library, for Kotlin backend services. It removes a lot of messaging-related
boilerplate, and can scale up to meet your throughput needs.

It supports the basics, like message routing based on attributes, and message deletion after processing. More advanced features
are also made easy - such as exponential backoff, and message delaying, to save you money during slow periods.

Most parts of Daisy are configurable, but the defaults are good enough for production use, and will help you build
a fast and reliable message processor; without repeating yourself across services.

The library is used and proved in production at [Adopt Animals](https://www.adopt.app), but it is developed independently
of anything else, including Kale Charity and my employer.

## Project goals

* ✅ Limit scope to SQS and SNS support to begin with
* ✅ Limit scope to workers deployed as independently scalable processes, paired/deployed with a partner microservice (write vs read separation)
* ✅ Support the consumption of messages using coroutines: https://github.com/Kotlin/kotlinx.coroutines
* ✅ Support message routing and serialization helpers: https://github.com/Kotlin/kotlinx.serialization
* ✅ Support changing message visibility to delay retries
* ✅ Gracefully scale to large amounts of messages (don't be the bottleneck)
* ✅ Support backoff strategies for scaling down processing of messages during slow periods
* 🟨 Include documentation, and example projects
* Support composition of processors - e.g. applying or removing GZIP compression
* Support permanently failing the processing of a message, by delivering to a DLQ

## Core

The library includes a `core` implementation. For now, the [demo](https://github.com/CarrotCodes/Daisy/tree/main/demo/src/main/kotlin/dev/skye/daisy)
projects described below are the best place to find examples of usage.

Before the API stabilises, and the library goes 1.0, this section will be expanded.

The first prerelease versions are published to Maven Central: `dev.skye.daisy:daisy-core:0.0.3`

You can use it with Gradle, for example:

```groovy
plugins {
    id 'org.jetbrains.kotlin.jvm' version '1.5.20'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation "dev.skye.daisy:daisy-core:0.0.3"

    // Kotlin
    implementation platform("org.jetbrains.kotlin:kotlin-bom")
    implementation platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.5.0")
    implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-jdk8"

    // AWS
    implementation platform("software.amazon.awssdk:bom:2.16.8")
    implementation "software.amazon.awssdk:sqs"

    // Metrics
    implementation "io.micrometer:micrometer-core:1.6.5"
}
```

## Ktor integration

Daisy can be integrated with Ktor, to start and stop message processing as the Ktor application starts and stops.

```kotlin
import dev.skye.daisy.DaisyFeature

// ...

install(DaisyFeature) {
    configuration = DaisyConfiguration(
        // ...
    )
}
```

See the demo projects section below for instructions on how to run the sample Ktor project.

## Demo projects

### Throughput demo

The throughput demo uses an in-memory SQS implementation to generate messages when requested, and will run until
terminated, or until zero messages are processed.

The demo shows a theoretical maximum for your machine, to prove that this library probably isn't the bottleneck in your
message processing pipeline. The example output is from my laptop (a 16" MacBook Pro), but in this demo, Daisy scales to
millions of messages on more powerful computers.

In real-world applications, where HTTP requests are used to perform actions on queues, you'll likely see much lower
throughput.

To run the demo project:
* Open the project in IntelliJ IDEA
* Run `main` in `ThroughputDemo.kt`, in the `demo` subproject

Example output:
```
messages.deleted{queue=https://test.local/0000/queue-1} throughput=130959/s
messages.deleted{queue=https://test.local/0000/queue-1-dlq} throughput=130760/s
messages.polled{queue=https://test.local/0000/queue-1-dlq} throughput=130810/s
messages.polled{queue=https://test.local/0000/queue-1} throughput=131020/s
messages.processed{queue=https://test.local/0000/queue-1} throughput=130990/s
messages.processed{queue=https://test.local/0000/queue-1-dlq} throughput=130780/s
messages.processed.total{} throughput=261771/s
```

### Ktor demo

See `KtorDemo.kt` in the `demo` subproject for an example of how to integrate. The demo includes a server that starts on
port 8080, and includes Prometheus metrics. The supplied dummy SQS implementation produces a low number of messages,
to generate some metrics.

Example application output:
```
[DefaultDispatcher-worker-1] INFO ktor.application - Autoreload is disabled because the development mode is off.
[DefaultDispatcher-worker-1] INFO ktor.application - Responding at http://0.0.0.0:8080
[DefaultDispatcher-worker-1] INFO ktor.application - Daisy starting...
[DefaultDispatcher-worker-9] INFO dev.skye.daisy.DaisyFeature - received message 8b3f1fba-3a32-4987-bbfb-8c3bed4a5d1f: MessageBody(message=hello, world!)
[DefaultDispatcher-worker-5] INFO dev.skye.daisy.DaisyFeature - received message c5b1f675-527d-4649-bd45-e90a05ed0e89: MessageBody(message=hello, world!)
```

Example metrics output:

```
➜ http localhost:8080/metrics | grep messages
# HELP messages_polled_total  
# TYPE messages_polled_total counter
messages_polled_total{queue="https://test.local/0000/queue-1",} 640.0
messages_polled_total{queue="https://test.local/0000/queue-1-dlq",} 630.0
# HELP messages_deleted_total  
# TYPE messages_deleted_total counter
messages_deleted_total{queue="https://test.local/0000/queue-1",} 640.0
messages_deleted_total{queue="https://test.local/0000/queue-1-dlq",} 630.0
# HELP messages_processed_total  
# TYPE messages_processed_total counter
messages_processed_total{queue="https://test.local/0000/queue-1",} 640.0
messages_processed_total{queue="https://test.local/0000/queue-1-dlq",} 630.0
```

## Copyright

This project is licensed under the Apache License: [LICENSE.txt](LICENSE.txt)

```
Copyright 2021 Skye Welch

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
