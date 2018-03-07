package io.micronaut.context.annotation;

import javax.inject.Qualifier;
import java.lang.annotation.*;

/**
 * <p>Allows configuration injection from the environment on a per property, field, method/constructor parameter basis.</p>
 *
 * @see ConfigurationProperties
 * @author Graeme Rocher
 * @since 1.0
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Qualifier
public @interface Value {
    /**
     * @return The name of the property to inject.
     */
    String value();
}
