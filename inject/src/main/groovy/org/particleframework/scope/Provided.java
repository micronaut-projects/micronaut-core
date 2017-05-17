package org.particleframework.scope;

import javax.inject.Scope;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Provided scope is used to define a bean that should not be considered a candidate for dependency injection because
 * it is provided by another bean. This scope is used when, for example, you have a factory bean that returns a bean
 * that also requires dependency injection.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Scope
@Retention(RUNTIME)
@Target(ElementType.TYPE)
public @interface Provided {
}
