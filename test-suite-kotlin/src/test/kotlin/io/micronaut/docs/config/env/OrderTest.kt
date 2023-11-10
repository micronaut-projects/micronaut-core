package io.micronaut.docs.config.env

import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext

class OrderTest: AnnotationSpec(){

    @Test
    fun testOrderOnFactories() {
        val applicationContext = ApplicationContext.run()
        val rateLimits = applicationContext.streamOfType(RateLimit::class.java)
            .toList()
        rateLimits.size.toLong() shouldBe 2
        rateLimits[0].limit.toLong() shouldBe 1000L
        rateLimits[1].limit.toLong() shouldBe 100L
        applicationContext.close()
    }
}
