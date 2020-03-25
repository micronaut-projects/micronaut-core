package io.micronaut.inject.failures.ctorcirculardependency;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;

@Prototype
public class ParameterizedBean {

    ParameterizedBean(@Parameter String name, @Parameter DoubleOptional doubleOptional) {}
}
