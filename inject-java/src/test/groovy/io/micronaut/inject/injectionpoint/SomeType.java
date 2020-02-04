package io.micronaut.inject.injectionpoint;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.InjectionPoint;

@Prototype
public class SomeType {

    private final String name;


    public SomeType(InjectionPoint<SomeType> injectionPoint) {
        this.name = injectionPoint
                .getAnnotationMetadata()
                .stringValue(SomeAnn.class)
                .orElse("no value");
    }

    public String getName() {
        return name;
    }
}
