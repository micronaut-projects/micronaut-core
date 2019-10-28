package io.micronaut.docs.function.client.aws;

import io.micronaut.function.FunctionBean;

import java.util.function.Function;

@FunctionBean("round")
public class RoundFunction implements Function<Float, Integer> {

    private final MathService mathService;

    public RoundFunction(MathService mathService) {
        this.mathService = mathService;
    }

    @Override
    public Integer apply(Float aFloat) {
        return mathService.round(aFloat);
    }
}
