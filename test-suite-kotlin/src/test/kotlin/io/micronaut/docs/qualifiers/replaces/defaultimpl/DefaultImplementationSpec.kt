package io.micronaut.docs.qualifiers.replaces.defaultimpl

import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.specs.StringSpec
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext

class DefaultImplementationSpec : StringSpec({

    "test the default implementation is replaced" {
        val ctx = BeanContext.run()
        val responseStrategy = ctx.getBean(ResponseStrategy::class.java)

        responseStrategy.shouldBeInstanceOf<CustomResponseStrategy>()

        ctx.close()
    }
})