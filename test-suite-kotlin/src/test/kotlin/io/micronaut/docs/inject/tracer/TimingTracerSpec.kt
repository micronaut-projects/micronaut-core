package io.micronaut.docs.inject.tracer

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanResolutionTracerProvider
import io.micronaut.context.env.Environment
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimingTracerSpec {

    @Test
    fun testRegisterTracer() {
        // tag::register[]
        val tracer = TimingTracer()
        ApplicationContext.builder()
            .properties(mapOf("spec.name" to "TimingTracerSpec"))
            .beanResolutionTracer(tracer) // <1>
            .start().use { context ->
                context.getBean(Greeter::class.java)
            }
        // end::register[]

        assertTrue(tracer.timings.containsKey(Greeter::class.java.name))
    }

    @Test
    fun testProviderEnabledInDevelopment() {
        val provider = TimingTracerProvider()

        assertTrue(provider.isEnabled(activation(setOf(Environment.DEVELOPMENT))))
        assertFalse(provider.isEnabled(activation(setOf("prod"))))
    }

    private fun activation(environmentNames: Set<String>) = object : BeanResolutionTracerProvider.Activation {
        override fun getEnvironmentNames(): Set<String> = environmentNames

        override fun isDiscoveryEnabled(): Boolean = false
    }
}
