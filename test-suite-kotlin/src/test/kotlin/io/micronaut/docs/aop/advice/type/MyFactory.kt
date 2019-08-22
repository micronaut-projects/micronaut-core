package io.micronaut.docs.aop.advice.type

import io.micronaut.cache.annotation.Cacheable
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Prototype
import io.micronaut.docs.aop.advice.MyBean

// tag::class[]
@Cacheable("my-cache")
@Factory
open class MyFactory {

    @Prototype
    open fun myBean(): MyBean {
        return MyBean()
    }
}
// end::class[]