package org.particleframework.context.annotation;

import java.lang.annotation.*;

/**
 * Allows a bean to specify that it replaces another bean. Note
 * that the bean to be replaced cannot be an {@link Infrastructure}
 * bean.
 *
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
