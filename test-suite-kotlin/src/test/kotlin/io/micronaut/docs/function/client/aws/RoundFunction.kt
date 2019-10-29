package io.micronaut.docs.function.client.aws

import io.micronaut.function.FunctionBean

import java.util.function.Function

@FunctionBean("round")
class RoundFunction(private val mathService: MathService) : Function<Float, Int> {

    override fun apply(aFloat: Float): Int {
        return mathService.round(aFloat)
    }
}
