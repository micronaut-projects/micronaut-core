package io.micronaut.docs.aop.introduction

// tag::imports[]

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext

import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class StubIntroduction implements MethodInterceptor<Object,Object> { // <1>

    @Override
    Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.getValue( // <2>
                Stub.class,
                context.getReturnType().getType()
        ).orElse(null) // <3>
    }
}
// end::class[]
