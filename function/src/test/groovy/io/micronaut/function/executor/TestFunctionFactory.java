package io.micronaut.function.executor;

import io.micronaut.context.annotation.Factory;
import io.micronaut.function.FunctionBean;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

@Factory
public class TestFunctionFactory {

    // This should work but is currently not implemented
    // the reason is because when @FunctionBean is defined on a factory
    // we do not go through and visit public Executable methods unless
    // it is an AOP proxy
    @FunctionBean("supplier")
    Supplier<String> get() {
        return () -> "myvalue";
    }

    // This should work but is currently not implemented
    // the reason is because when @FunctionBean is defined on a factory
    // we do not go through and visit public Executable methods unless
    // it is an AOP proxy
    @FunctionBean("round")
    Function<Double, Long> round() {
        return (doub) -> Math.round(doub.doubleValue());
    }

    @FunctionBean("upper")
    Function<Name, Name> upper() {
        return (n) -> n.setName(n.getName().toUpperCase());
    }

    @FunctionBean("fullname")
    BiFunction<String, String, Name> fullname() {
        return (s, s2) -> {
            Name name = new Name();
            name.setName(s + " " + s2);
            return name;
        };
    }

    static class Name {
        private String name;

        public String getName() {
            return name;
        }

        public Name setName(String name) {
            this.name = name;
            return this;
        }
    }
}

