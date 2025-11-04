package io.micronaut.docs.factories.primitive

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class EngineSpec {
    @Test
    fun testEngine() {
        ApplicationContext.run().use { beanContext ->
            val engine =
                beanContext.getBean(
                    V8Engine::class.java
                )
            Assertions.assertEquals(
                8,
                engine.cylinders
            )
        }
    }
}