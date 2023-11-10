package io.micronaut.docs.lifecycle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.BeanContext

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
