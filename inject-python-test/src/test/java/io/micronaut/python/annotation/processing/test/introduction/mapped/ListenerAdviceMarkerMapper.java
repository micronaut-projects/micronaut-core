package io.micronaut.python.annotation.processing.test.introduction.mapped;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.List;

public class ListenerAdviceMarkerMapper implements TypedAnnotationMapper<ListenerAdviceMarker> {

    @Override
    public Class<ListenerAdviceMarker> annotationType() {
        return ListenerAdviceMarker.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<ListenerAdviceMarker> annotation, VisitorContext visitorContext) {
        return List.of(AnnotationValue.builder(ListenerAdvice.class).build());
    }
}
