package io.micronaut.docs.qualifiers.replaces.defaultimpl

import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.specs.StringSpec
import io.micronaut.context.DefaultBeanContext

class DefaultImplementationSpec : StringSpec({

    "test the default implementation is replaced" {
        val ctx = DefaultBeanContext().start()
        val responseStrategy = ctx.getBean(ResponseStrategy::class.java)

        responseStrategy.shouldBeInstanceOf<CustomResponseStrategy>()
    }
})