package io.micronaut.docs.factories.primitive

import io.micronaut.context.BeanContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class EngineSpec {
    @Test
    fun testEngine() {
        BeanContext.run().use { beanContext ->
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