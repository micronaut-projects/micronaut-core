package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;

public class ToTransformRetentionTransformer implements NamedAnnotationTransformer {

    @Nonnull
    @Override
    public String getName() {
        return ToTransformRetention.class.getName();
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder(annotation, RetentionPolicy.RUNTIME).build()
        );
    }
}
