package io.micronaut.reflection;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.AnnotationValue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * A customizer deriving a member from the value of the annotation, the way a processor extension derives the
 * validator classes of a constraint. It supports one annotation type so that registering it as a service
 * leaves the metadata of every other annotation of the test module untouched.
 */
public final class DerivingCustomizer implements ReflectionAnnotationCustomizer {

    @Override
    public boolean supports(Class<? extends Annotation> annotationType) {
        return annotationType == Customized.class;
    }

    @Override
    public void customize(Annotation annotation, Map<CharSequence, Object> values) {
        values.put("derived", "from-" + values.getOrDefault("value", "nothing"));
    }

    /**
     * The contract of a family this customizer speaks for is retainable, though it carries no marker of its own.
     */
    @Override
    public boolean isRetainable(Class<? extends Annotation> annotationType) {
        return annotationType == Governed.class;
    }

    /**
     * An override declared as {@link Overrides}, stated as the {@code @AliasFor} a transformer would produce.
     */
    @Override
    public List<AnnotationValue<AliasFor>> aliasesOf(Method member) {
        Overrides overrides = member.getAnnotation(Overrides.class);
        if (overrides == null) {
            return List.of();
        }
        return List.of(AnnotationValue.builder(AliasFor.class)
            .member("annotationName", overrides.annotation().getName())
            .member("member", overrides.member())
            .member("applyDefault", true)
            .build());
    }
}
