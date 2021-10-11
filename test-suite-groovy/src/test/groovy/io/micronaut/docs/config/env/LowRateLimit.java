package io.micronaut.docs.config.env;


import java.time.Duration;

public class LowRateLimit extends RateLimit {

    public LowRateLimit(Duration period, Integer limit) {
        super(period, limit);
    }
}
