package io.micronaut.kotlin.processing.aop.hotswap

import io.micronaut.aop.Around
import io.micronaut.kotlin.processing.aop.proxytarget.Mutating
import jakarta.inject.Singleton

@Around(proxyTarget = true, hotswap = true)
@Singleton
open class HotswappableProxyingClass {

    var invocationCount = 0

    @Mutating("name")
    open fun test(name: String): String {
        invocationCount++
        return "Name is $name"
    }

    open fun test2(another: String): String {
        invocationCount++
        return "Name is $another"
    }
}
