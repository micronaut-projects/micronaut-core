package io.micronaut.docs.function.client.aws;

import io.micronaut.context.annotation.Value;

import javax.inject.Singleton;

@Singleton
public class MathService {
    @Value("${math.multiplier:1}")
    private Integer multiplier = 1;

    public int round(float value) {
        return Math.round(value) * multiplier;
    }

    public Integer max() {
        return Integer.MAX_VALUE;
    }

    public long sum(Sum sum) {
        return sum.getA() + sum.getB();
    }
}

