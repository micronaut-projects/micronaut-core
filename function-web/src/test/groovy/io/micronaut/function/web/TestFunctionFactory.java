package io.micronaut.function.web;

import io.micronaut.context.annotation.Factory;
import io.micronaut.function.FunctionBean;

import java.util.function.Supplier;

@Factory
public class TestFunctionFactory {

    // This should work but is currently not implemented
    // the reason is because when @FunctionBean is defined on a factory
    // we do not go through and visit public Executable methods unless
    // it is an AOP proxy
    @FunctionBean("java/supplier/string")
    Supplier<String> get() {
        return () -> "myvalue";
    }
}
