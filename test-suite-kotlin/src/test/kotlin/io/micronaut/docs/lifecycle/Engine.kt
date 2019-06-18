package io.micronaut.docs.lifecycle

interface Engine {
    val cylinders: Int

    fun start(): String
}
