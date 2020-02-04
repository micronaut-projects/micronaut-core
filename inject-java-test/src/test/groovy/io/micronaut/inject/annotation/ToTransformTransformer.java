package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.List;

public class ToTransformTransformer implements TypedAnnotationTransformer<ToTransform> {
    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<ToTransform> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder("test.Test").build()
        );
    }

    @Override
    public Class<ToTransform> annotationType() {
        return ToTransform.class;
    }
}
