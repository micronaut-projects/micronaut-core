package io.micronaut.docs.inject.typed

import io.micronaut.context.BeanContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import jakarta.inject.Inject

// tag::class[]
@MicronautTest
class EngineSpec {
    @Inject
    lateinit var beanContext: BeanContext

    @Test
    fun testEngine() {
        assertThrows(NoSuchBeanException::class.java) {
            beanContext.getBean(V8Engine::class.java) // <1>
        }

        val engine = beanContext.getBean(Engine::class.java) // <2>
        assertTrue(engine is V8Engine)
    }
}
// end::class[]
