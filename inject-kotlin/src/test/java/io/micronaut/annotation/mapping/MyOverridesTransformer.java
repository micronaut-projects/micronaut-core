package io.micronaut.annotation.mapping;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.List;

/**
 * Transforms {@link MyOverrides} to {@code @AliasFor}, mirroring how
 * {@code jakarta.validation.OverridesAttribute} would be remapped.
 */
public class MyOverridesTransformer implements TypedAnnotationTransformer<MyOverrides> {

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<MyOverrides> annotation, VisitorContext visitorContext) {
        return List.of(toAliasFor(annotation));
    }

    @Override
    public Class<MyOverrides> annotationType() {
        return MyOverrides.class;
    }

    static AnnotationValue<AliasFor> toAliasFor(AnnotationValue<MyOverrides> annotation) {
        AnnotationValueBuilder<AliasFor> builder = AnnotationValue.builder(AliasFor.class);
        annotation.annotationClassValue("constraint").ifPresent(constraint -> builder.member("annotationName", constraint.getName()));
        annotation.stringValue("name").ifPresent(name -> builder.member("member", name));
        annotation.intValue("constraintIndex").ifPresent(index -> builder.member("index", index));
        builder.member("applyDefault", true);
        return builder.build();
    }
}
