package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.kotlin.processing.aop.simple.Mutating
import jakarta.inject.Singleton

@Stub
@Mutating("name")
@Singleton
interface InterfaceIntroductionClass<A> : SuperInterface<A> {

    fun test(name: String): String
    fun test(name: String, age: Int): String
}
