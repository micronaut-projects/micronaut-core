package io.micronaut.docs.context.env

import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.DefaultEnvironment
import io.micronaut.context.env.Environment
import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

class DefaultEnvironmentSpec : StringSpec({
    "test environment system property resolve"() {
        System.setProperty("test.foo.bar", "10")
        val env = DefaultEnvironment("test").start()

        assertEquals(10, (env.getProperty("test.foo.bar", Int::class.java).get() as Int).toLong())
        assertEquals(10, (env.getRequiredProperty("test.foo.bar", Int::class.java) as Int).toLong())
        assertEquals(10, (env.getProperty("test.foo.bar", Int::class.java, 20) as Int).toLong())

        System.setProperty("test.foo.bar", "")
    }

    // tag::disableEnvDeduction[]
    "test disable environment deduction via builder"() {
        val ctx = ApplicationContext.build().deduceEnvironment(false).start()
        assertFalse(ctx.getEnvironment().getActiveNames().contains(Environment.TEST))
        ctx.close()
    }
    // end::disableEnvDeduction[]
})
