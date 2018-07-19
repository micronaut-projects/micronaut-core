package io.micronaut.inject.value.nullablevalue;

import io.micronaut.context.annotation.Value;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class A {

    public final String nullConstructorArg;
    public final String nonNullConstructorArg;
    public String nullMethodArg;
    public String nonNullMethodArg;
    public @Value("${doesnt.exist}") @Nullable String nullField;
    public @Value("${exists.x}") String nonNullField;


    public A(@Value("${doesnt.exist}") @Nullable String nullConstructorArg,
             @Value("${exists.x}") String nonNullConstructorArg) {
        this.nullConstructorArg = nullConstructorArg;
        this.nonNullConstructorArg = nonNullConstructorArg;
    }

    @Inject
    void injectedMethod(@Value("${doesnt.exist}") @Nullable String nullMethodArg,
                        @Value("${exists.x}") String nonNullMethodArg) {
        this.nullMethodArg = nullMethodArg;
        this.nonNullMethodArg = nonNullMethodArg;
    }
}
