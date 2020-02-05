package io.micronaut.docs.config.env

import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter
import io.micronaut.core.order.Ordered
import java.time.Duration

@EachProperty(value = "ratelimits", list = true) // <1>
class RateLimitsConfiguration
    constructor(@param:Parameter private val index: Int) // <3>
    : Ordered { // <2>

    var period: Duration? = null
    var limit: Int? = null

    override fun getOrder(): Int {
        return index
    }

}
