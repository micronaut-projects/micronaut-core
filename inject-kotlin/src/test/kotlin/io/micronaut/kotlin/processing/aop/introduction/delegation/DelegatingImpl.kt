package io.micronaut.kotlin.processing.aop.introduction.delegation

class DelegatingImpl : Delegating {

    override fun test(): String {
        return "good"
    }
}
