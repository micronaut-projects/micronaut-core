package org.particleframework.context.annotation;

import javax.inject.Qualifier;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>A {@link Qualifier} that indicates that this bean is the primary bean that should be selected in the case of multiple possible interface implementations.</p>
 *
 * @author Graeme Rocher
 * @see Qualifier @Qualifier
 */
@Qualifier
@Documented
@Retention(RUNTIME)
@Target(ElementType.TYPE)
public @interface Primary {
}
