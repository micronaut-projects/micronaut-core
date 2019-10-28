package io.micronaut.docs.function.client.aws;

import io.micronaut.function.FunctionBean;

import java.util.function.Supplier;

@FunctionBean("max")
public class MaxFunction implements Supplier<Integer> {

    private final MathService mathService;

    public MaxFunction(MathService mathService) {
        this.mathService = mathService;
    }

    @Override
    public Integer get() {
        return mathService.max();
    }
}
