package io.micronaut.repro

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PostConstructDoubleInvocationKspTest {

    @Test
    fun postConstructCalledOnceWhenCreateThenRegister() {
        val ctx = ApplicationContext.run(mapOf("spec.name" to "PostConstructDoubleInvocationKspTest"))
        try {
            val bean = ctx.createBean(MyBean::class.java)
            // createBean should invoke @PostConstruct exactly once
            assertEquals(1, bean.initCount, "createBean should call @PostConstruct exactly once")

            // Register the same instance as a singleton in the context
            ctx.registerSingleton(MyBean::class.java, bean)

            // registerSingleton should NOT invoke @PostConstruct again for the same instance
            assertEquals(1, bean.initCount, "registerSingleton should not invoke @PostConstruct again for the same instance")

            // Ensure the registered instance is the one available from the context
            assertSame(bean, ctx.getBean(MyBean::class.java))
        } finally {
            ctx.close()
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "PostConstructDoubleInvocationKspTest")
    class MyBean {
        var initCount: Int = 0
            private set

        @PostConstruct
        fun init() {
            initCount++
        }
    }
}
