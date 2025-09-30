package io.micronaut.docs.config.env

import java.time.Duration

open class RateLimit(val period: Duration?, val limit: Int) 
