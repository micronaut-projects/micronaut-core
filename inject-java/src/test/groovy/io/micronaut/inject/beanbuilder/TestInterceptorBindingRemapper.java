package io.micronaut.inject.beanbuilder;

import java.util.List;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.AnnotationRemapper;
import io.micronaut.inject.visitor.VisitorContext;

public class TestInterceptorBindingRemapper implements AnnotationRemapper {
    @Override
    public String getPackageName() {
        return "io.micronaut.inject.beanbuilder.another";
    }

    @Override
    public List<AnnotationValue<?>> remap(AnnotationValue<?> annotation, VisitorContext visitorContext) {
        return TestInterceptorBindingTransformer.ANNOTATION_VALUES;
    }
}
