package io.micronaut.kotlin.processing.aop.introduction.temp

import io.micronaut.kotlin.processing.aop.introduction.*
import io.micronaut.context.annotation.*

@ListenerAdvice
@Stub
@jakarta.inject.Singleton
interface MyBean  {

    @Executable
    fun getBar(): String

    @Executable
    fun getFoo() : String {
        return "good"
    }
}
