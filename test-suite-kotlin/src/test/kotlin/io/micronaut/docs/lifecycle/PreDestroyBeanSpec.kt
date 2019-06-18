package io.micronaut.docs.lifecycle

import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.specs.StringSpec
import io.micronaut.context.BeanContext

class PreDestroyBeanSpec : StringSpec({

    "testBeanClosingOnContextClose" {
        // tag::start[]
        val ctx = BeanContext.build().start()
        val preDestroyBean = ctx.getBean(PreDestroyBean::class.java)
        val connection = ctx.getBean(Connection::class.java)
        ctx.stop()
        // end::start[]


        preDestroyBean.stopped.get().shouldBeTrue()
        connection.stopped.get().shouldBeTrue()
    }
})
