package io.micronaut.aop.factory.mapped;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.ArrayList;
import java.util.List;

public class TestConfigurationMapper implements TypedAnnotationMapper<TestConfiguration> {
    @Override
    public Class<TestConfiguration> annotationType() {
        return TestConfiguration.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<TestConfiguration> annotation, VisitorContext visitorContext) {
        List<AnnotationValue<?>> mappedAnnotations = new ArrayList<>(2);
        mappedAnnotations.add(AnnotationValue.builder(Factory.class)
                .build());
        mappedAnnotations.add(AnnotationValue.builder(TestSingletonAdvice.class).build());
        return mappedAnnotations;
    }
}
