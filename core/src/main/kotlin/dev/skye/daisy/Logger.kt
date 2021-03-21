package dev.skye.daisy

import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal inline fun <reified T> logger(): Logger {
    return LoggerFactory.getLogger(T::class.java)
}
