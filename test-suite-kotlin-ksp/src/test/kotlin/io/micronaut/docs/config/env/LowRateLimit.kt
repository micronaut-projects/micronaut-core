package io.micronaut.docs.config.env

import java.time.Duration

class LowRateLimit(period: Duration?, limit: Int) : RateLimit(period, limit) 
