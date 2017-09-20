package org.particleframework.context.annotation;

import java.lang.annotation.*;

/**
 * For specifying multiple requirements
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface Requirements {
    Requires[] value();
}
