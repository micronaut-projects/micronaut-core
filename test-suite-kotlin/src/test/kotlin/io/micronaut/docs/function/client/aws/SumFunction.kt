package io.micronaut.docs.function.client.aws

import io.micronaut.function.FunctionBean

import java.util.function.Function

@FunctionBean("sum")
class SumFunction(private val mathService: MathService) : Function<Sum, Long> {

    override fun apply(sum: Sum): Long {
        return mathService.sum(sum)
    }
}
