package io.micronaut.function.web;

import io.micronaut.context.annotation.Factory;
import io.micronaut.function.FunctionBean;

import java.util.function.Supplier;

@Factory
public class TestFunctionFactory {

    @FunctionBean("java/supplier/string")
    Supplier<String> get() {
        return () -> "myvalue";
    }
}
