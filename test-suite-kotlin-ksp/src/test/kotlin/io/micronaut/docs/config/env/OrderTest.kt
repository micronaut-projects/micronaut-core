package io.micronaut.docs.config.env

import io.micronaut.context.ApplicationContext
import org.junit.Assert
import org.junit.jupiter.api.Test
import java.util.stream.Collectors

class OrderTest {

    @Test
    fun testOrderOnFactories() {
        val applicationContext = ApplicationContext.run()
        val rateLimits = applicationContext.streamOfType(RateLimit::class.java)
                .collect(Collectors.toList())
        Assert.assertEquals(
                2,
                rateLimits.size
                        .toLong())
        Assert.assertEquals(1000L, rateLimits[0].limit.toLong())
        Assert.assertEquals(100L, rateLimits[1].limit.toLong())
        applicationContext.close()
    }
}
