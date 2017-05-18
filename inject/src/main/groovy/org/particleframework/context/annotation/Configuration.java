package org.particleframework.context.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * A configuration is a grouping of bean definitions under a package. A configuration can have requirements applied to it with {@link Requires} such that the entire configuration only loads of the requirements are met
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Target(ElementType.PACKAGE)
public @interface Configuration {
}
