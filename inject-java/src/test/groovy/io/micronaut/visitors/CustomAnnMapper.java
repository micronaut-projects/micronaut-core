package io.micronaut.visitors;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.List;

public class CustomAnnMapper implements TypedAnnotationMapper<CustomAnn> {
    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<CustomAnn> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(annotation);
    }

    @Override
    public Class<CustomAnn> annotationType() {
        return CustomAnn.class;
    }
}
