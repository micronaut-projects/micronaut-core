package io.micronaut.annotation.mapping;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.List;

/**
 * Transforms the {@link MyOverridesList} container to {@code @AliasFor} values.
 */
public class MyOverridesListTransformer implements TypedAnnotationTransformer<MyOverridesList> {

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<MyOverridesList> annotation, VisitorContext visitorContext) {
        return annotation.<MyOverrides>getAnnotations(AnnotationMetadata.VALUE_MEMBER)
            .stream()
            .<AnnotationValue<?>>map(MyOverridesTransformer::toAliasFor)
            .toList();
    }

    @Override
    public Class<MyOverridesList> annotationType() {
        return MyOverridesList.class;
    }
}
