package io.micronaut.docs.aop.introduction

// tag::imports[]

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext

import javax.inject.Singleton

// end::imports[]

// tag::class[]
@Singleton
class StubIntroduction : MethodInterceptor<Any, Any> { // <1>

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        return context.getValue<Any>( // <2>
                Stub::class.java,
                context.returnType.type
        ).orElse(null) // <3>
    }
}
// end::class[]
