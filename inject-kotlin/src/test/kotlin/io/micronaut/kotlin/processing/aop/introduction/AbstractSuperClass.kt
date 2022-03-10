package io.micronaut.kotlin.processing.aop.introduction

abstract class AbstractSuperClass : SuperInterface<Any> {

    abstract fun test(name: String, age: Int): String
}
