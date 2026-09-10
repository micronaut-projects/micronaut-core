package io.micronaut.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A composed member of the {@link Governed} family: it composes {@link Bounded} and overrides its
 * {@code least} in terms of {@link Overrides} rather than {@code @AliasFor}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
@Governed
@Bounded(least = 1)
public @interface Ranged {

    @Overrides(annotation = Bounded.class, member = "least")
    int least() default 1;
}
