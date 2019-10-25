package io.micronaut.docs.function.client.aws;

import io.micronaut.function.FunctionBean;

import java.util.function.Function;

@FunctionBean("sum")
public class SumFunction implements Function<Sum, Long> {

    private final MathService mathService;

    public SumFunction(MathService mathService) {
        this.mathService = mathService;
    }

    @Override
    public Long apply(Sum sum) {
        return mathService.sum(sum);
    }
}
