package io.micronaut.annotation.mapping;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.List;

public class NonNullProducingMapper implements TypedAnnotationMapper<NonNullStereotyped> {
    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<NonNullStereotyped> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(AnnotationValue.builder("jakarta.annotation.Nullable").build());
    }

    @Override
    public Class<NonNullStereotyped> annotationType() {
        return NonNullStereotyped.class;
    }
}
