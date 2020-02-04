package io.micronaut.inject.injectionpoint;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.InjectionPoint;

@Factory
public class SomeFactory {

    @Prototype
    SomeBean someBean(InjectionPoint<SomeBean> someBean) {
        final String str = someBean
                .getAnnotationMetadata()
                .stringValue(SomeAnn.class)
                .orElse("no value");

        return new SomeBean(str);
    }
}
