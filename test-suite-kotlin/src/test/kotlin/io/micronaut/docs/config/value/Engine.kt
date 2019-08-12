package io.micronaut.docs.config.value

interface Engine {
    val cylinders: Int

    fun start(): String
}
