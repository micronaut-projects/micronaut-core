package io.micronaut.docs.config.env

//tag::clazz[]
import io.micronaut.context.annotation.Factory
import io.micronaut.core.annotation.Order

import javax.inject.Singleton
import java.time.Duration

@Factory
class RateLimitsFactory {

    @Singleton
    @Order(20)
    LowRateLimit rateLimit2() {
        new LowRateLimit(Duration.ofMinutes(50), 100);
    }

    @Singleton
    @Order(10)
    HighRateLimit rateLimit1() {
        new HighRateLimit(Duration.ofMinutes(50), 1000);
    }
}
//end::clazz[]
