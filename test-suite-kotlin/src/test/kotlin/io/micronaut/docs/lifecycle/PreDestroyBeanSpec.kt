package io.micronaut.docs.lifecycle

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.BeanContext
import org.junit.Test

import org.junit.Assert.assertTrue

class PreDestroyBeanSpec: StringSpec() {

    init {
        "test bean closing on context close" {
            // tag::start[]
            val ctx = BeanContext.run()
            val preDestroyBean = ctx.getBean(PreDestroyBean::class.java)
            val connection = ctx.getBean(Connection::class.java)
            ctx.stop()
            // end::start[]

            preDestroyBean.stopped.get() shouldBe true
            connection.stopped.get() shouldBe true
        }
    }
}
