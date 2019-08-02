package io.micronaut.docs.config.mapFormat

interface Engine {
    val sensors: Map<*, *>?
    fun start(): String
}
