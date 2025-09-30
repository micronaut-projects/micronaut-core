package io.micronaut.docs.config.env

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.stream.Collectors

class OrderTest {

    @Test
    fun testOrderOnFactories() {
        val applicationContext = ApplicationContext.run()
        val rateLimits = applicationContext.streamOfType(RateLimit::class.java)
                .collect(Collectors.toList())
        assertEquals(
                2,
                rateLimits.size
                        .toLong())
        assertEquals(1000L, rateLimits[0].limit.toLong())
        assertEquals(100L, rateLimits[1].limit.toLong())
        applicationContext.close()
    }
}
