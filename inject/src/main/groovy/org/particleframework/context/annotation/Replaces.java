package org.particleframework.context.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows a bean to specify that it replaces another bean. Note
 * that the bean to be replaced cannot be an {@link org.particleframework.scope.Infrastructure}
 * bean.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Replaces {

    /**
     *
     * @return The bean that this bean replaces
     */
    Class value();
}
