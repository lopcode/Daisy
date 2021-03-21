package dev.skye.daisy

import io.ktor.application.call
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

fun main() {

    embeddedServer(CIO, port = 8080) {
        routing {
            get("/metrics") {
                call.respondText("Hello, world!")
            }
        }
    }.start(wait = true)
}
