package io.micronaut.inject.annotation;

import io.micronaut.inject.reflection.Customized;

import java.lang.annotation.Annotation;
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
}
