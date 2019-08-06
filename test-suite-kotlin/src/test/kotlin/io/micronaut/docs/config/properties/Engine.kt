package io.micronaut.docs.config.properties

interface Engine {
    val cylinders: Int

    fun start(): String
}
