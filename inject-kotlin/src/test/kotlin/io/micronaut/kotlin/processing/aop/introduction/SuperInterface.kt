package io.micronaut.kotlin.processing.aop.introduction

interface SuperInterface<A> {

    fun testGenericsFromType(name: A, age: Int): A
}
