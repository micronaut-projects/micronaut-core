package io.micronaut.docs.config.env;

import java.time.Duration;

public class RateLimit {

    private Duration period;
    private Integer limit;

    public RateLimit(Duration period, Integer limit) {
        this.period = period;
        this.limit = limit;
    }

    public Duration getPeriod() {
        return period;
    }

    public Integer getLimit() {
        return limit;
    }
}
