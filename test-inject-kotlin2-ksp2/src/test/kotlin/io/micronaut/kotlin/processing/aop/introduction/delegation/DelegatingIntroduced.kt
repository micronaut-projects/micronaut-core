package io.micronaut.kotlin.processing.aop.introduction.delegation

@DelegationAdvice
interface DelegatingIntroduced : Delegating {
    fun test2(): String
}
