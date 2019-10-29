package io.micronaut.docs.function.client.aws

import io.micronaut.context.annotation.Value

import javax.inject.Singleton

@Singleton
class MathService {
    @Value("\${math.multiplier:1}")
    private val multiplier = 1

    fun round(value: Float): Int {
        return Math.round(value) * multiplier
    }

    fun max(): Int {
        return Integer.MAX_VALUE
    }

    fun sum(sum: Sum): Long {
        return (sum.a!! + sum.b!!).toLong()
    }
}

