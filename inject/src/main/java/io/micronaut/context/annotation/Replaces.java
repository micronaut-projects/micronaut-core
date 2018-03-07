package io.micronaut.context.annotation;

import java.lang.annotation.*;

/**
 * <p>Allows a bean to specify that it replaces another bean. Note
 * that the bean to be replaced cannot be an {@link Infrastructure}
 * bean.</p>
 *
 * @see Infrastructure
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface Replaces {

    /**
     * @return The bean type that this bean replaces
     */
    Class value();
}
