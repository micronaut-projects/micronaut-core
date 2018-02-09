package org.particleframework.context.annotation;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>Context scope indicates that the classes life cycle is bound to that of the {@link org.particleframework.context.BeanContext}
 * and it should be initialized and shutdown during startup and shutdown of the underlying {@link org.particleframework.context.BeanContext}</p>
 *
 * <p>Particle by default treats all {@link Singleton} bean definitions as lazy and will only load them on demand by annotating a bean with @Context you can ensure that the bean is loaded at the same time as the context</p>
 *
 * <p>WARNING: This annotation should be used sparingly as Particle is designed in such a way as to encourage minimal bean creation during startup.</p>
 *
 * <p>NOTE: This annotation can also be used as a meta annotation</p>
 *
 * @see Singleton @Singleton
 */
@Singleton
@Documented
@Retention(RUNTIME)
public @interface Context {
}
