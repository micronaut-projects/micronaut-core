package io.micronaut.docs.config.env

//tag::clazz[]
import io.micronaut.context.annotation.Factory
import io.micronaut.core.annotation.Order
import java.time.Duration
import javax.inject.Singleton

@Factory
class RateLimitsFactory {

    @Singleton
    @Order(20)
    fun rateLimit2(): LowRateLimit {
        return LowRateLimit(Duration.ofMinutes(50), 100)
    }

    @Singleton
    @Order(10)
    fun rateLimit1(): HighRateLimit {
        return HighRateLimit(Duration.ofMinutes(50), 1000)
    }
}
//end::clazz[]
