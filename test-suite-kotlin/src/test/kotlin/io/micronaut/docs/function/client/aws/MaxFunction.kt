package io.micronaut.docs.function.client.aws

import io.micronaut.function.FunctionBean

import java.util.function.Supplier

@FunctionBean("max")
class MaxFunction(private val mathService: MathService) : Supplier<Int> {

    override fun get(): Int {
        return mathService.max()
    }
}
