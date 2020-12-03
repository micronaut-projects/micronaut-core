package io.micronaut.docs.config.env;

import java.time.Duration;

public class HighRateLimit extends RateLimit {

    public HighRateLimit(Duration period, Integer limit) {
        super(period, limit);
    }
}
