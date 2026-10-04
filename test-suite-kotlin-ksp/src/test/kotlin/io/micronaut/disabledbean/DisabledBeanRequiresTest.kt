package io.micronaut.disabledbean

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.DisabledBeanException
import jakarta.inject.Singleton
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DisabledBeanRequiresTest {
    @Test
    fun testConditionalMakeAnotherBeanUnavailable() {
        val ctx = ApplicationContext.run()
        try {
            val opt = ctx.findBean(AnotherBean::class.java)
            // Expected: AnotherBean should not be available because MyBean is disabled via DisabledBeanException
            assertTrue(opt.isEmpty, "AnotherBean should not be available when MyBean is disabled")
        } finally {
            ctx.close()
        }
    }
}

class MyBean

class AnotherBean(val myBean: MyBean)

@Factory
class MyFactory {
    @Singleton
    fun myBean(): MyBean {
        throw DisabledBeanException("MyBean Disabled")
    }

    @Singleton
    @Requires(bean = MyBean::class)
    fun anotherBean(myBean: MyBean): AnotherBean {
        return AnotherBean(myBean)
    }
}
