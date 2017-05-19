package org.particleframework.scope;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>Infrastructure scope represents a bean that cannot be overridden or replaced
 * because it is critical to the functioning of the system.</p>
 *
 * @see Singleton @Singleton
 */
@Context
@Documented
@Retention(RUNTIME)
@Target(ElementType.TYPE)
public @interface Infrastructure {
}
