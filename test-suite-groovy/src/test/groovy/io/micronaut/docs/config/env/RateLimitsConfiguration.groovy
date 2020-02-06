package io.micronaut.docs.config.env

import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter
import io.micronaut.core.order.Ordered

import java.time.Duration

@EachProperty(value = "ratelimits", list = true) // <1>
class RateLimitsConfiguration implements Ordered { // <2>

    private final Integer index
    Duration period
    Integer limit

    RateLimitsConfiguration(@Parameter Integer index) { // <3>
        this.index = index
    }

    @Override
    int getOrder() {
        index
    }
}
