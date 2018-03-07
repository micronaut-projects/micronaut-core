package io.micronaut.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * <p>A configuration is a grouping of bean definitions under a package. A configuration can have requirements applied to it with {@link Requires} such that the entire configuration only loads of the requirements are met</p>
 *
 * @see Requires
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Target(ElementType.PACKAGE)
public @interface Configuration {
}
