package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.kotlin.processing.aop.simple.Mutating
import jakarta.inject.Singleton

@Stub
@Singleton
@Mutating("name")
abstract class AbstractClass : AbstractSuperClass() {

    abstract fun test(name: String): String

    open fun nonAbstract(name: String): String {
        return test(name)
    }
}
