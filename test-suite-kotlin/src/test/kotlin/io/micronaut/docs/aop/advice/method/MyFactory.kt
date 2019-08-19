package io.micronaut.docs.aop.advice.method

import io.micronaut.cache.annotation.Cacheable
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Prototype
import io.micronaut.docs.aop.advice.MyBean

// tag::class[]
@Factory
open class MyFactory {

    @Prototype
    @Cacheable("my-cache")
    open fun myBean(): MyBean {
        return MyBean()
    }
}
// end::class[]