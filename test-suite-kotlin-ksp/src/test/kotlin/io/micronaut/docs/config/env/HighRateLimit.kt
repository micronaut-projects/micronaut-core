package io.micronaut.docs.config.env

import java.time.Duration

class HighRateLimit(period: Duration?, limit: Int) : RateLimit(period, limit) 
