package io.micronaut.kotlin.processing.aop.introduction

@RepoDef
interface MyRepo : SuperRepo {

    fun aBefore(): String
    override fun findAll(): List<Int>
    fun xAfter(): String
}
