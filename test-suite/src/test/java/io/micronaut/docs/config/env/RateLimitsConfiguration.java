package io.micronaut.docs.config.env;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.order.Ordered;

import java.time.Duration;

@EachProperty(value = "ratelimits", list = true) // <1>
public class RateLimitsConfiguration implements Ordered { // <2>

    private final Integer index;
    private Duration period;
    private Integer limit;

    RateLimitsConfiguration(@Parameter Integer index) { // <3>
        this.index = index;
    }

    @Override
    public int getOrder() {
        return index;
    }

    public Duration getPeriod() {
        return period;
    }

    public void setPeriod(Duration period) {
        this.period = period;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

}
