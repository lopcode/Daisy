# Daisy 🌼

Daisy is a work-in-progress project to formalise some Kotlin inter-service messaging primitives that I've written a few times for different projects.

Producing and consuming messages in distributed or eventually consistent systems can be tricky, and must be built on fast, reliable, and tested foundations.

Kotlin, its standard library, and extension libraries (for coroutines and serialization) offer some excellent tools for server-side development, so we use those as the basis for the library.

The library will likely eventually be used and proved in production at [Adopt Animals](https://www.adopt.app), but it is developed independently of anything else, including Kale Charity and my employer.

## Project goals

* ✅ Limit scope to SQS and SNS support to begin with
* ✅ Limit scope to workers deployed as independently scalable processes, paired/deployed with a partner microservice (write vs read separation)
* ✅ Support the consumption of messages using coroutines: https://github.com/Kotlin/kotlinx.coroutines
* ✅ Support message routing and serialization helpers: https://github.com/Kotlin/kotlinx.serialization
* ✅ Support changing message visibility to delay retries
* Support composition of processors - e.g. applying or removing GZIP compression
* Support backoff strategies for scaling down processing of messages during slow periods
* Support permanently failing the processing of a message, by delivering to a DLQ
* Gracefully scale to large amounts of messages (don't be the bottleneck)
* Include documentation, and an example project

### Code goals
* Keep unit test coverage high (~90%)
* Integration test using Docker Compose
* Investigate correctness testing with Lincheck: https://github.com/Kotlin/kotlinx-lincheck
* Investigate benchmarking tools (also Lincheck/JMH?)
* Try to keep dependency graph lean - if more systems are to be supported, consider subprojects and a BOM
* Do CI/CD with GitHub Actions

## Running demo project

A demo project is included, to show how to use Daisy, and to prove that it isn't the bottleneck in your message
processing pipeline.

The program uses a fake SQS client to generate messages when requested, and will run until terminated, or until
zero messages are processed.

In real-world applications, where HTTP requests are used to perform actions on queues, you'll see much lower
throughput per instance.

To run the demo project:
* Open the project in IntelliJ IDEA
* Run `main` in `Demo.kt`, in the `demo` subproject

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