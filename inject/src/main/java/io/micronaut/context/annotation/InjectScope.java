package io.micronaut.context.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>An annotation that can be declared on a constructor or method parameter that indicates
 * that the injected bean should be destroyed after injection completes.</p>
 *
 * <p>More specifically after a constructor or method which is annotated with {@link jakarta.inject.Inject} completes execution then any parameters annotated with {@link io.micronaut.context.annotation.InjectScope} which do not declare a specific scope such as {@link jakarta.inject.Singleton} will be destroyed resulting in the execution of {@link jakarta.annotation.PreDestroy} handlers.</p>
 *
 * @author graemerocher
 * @since 3.1.0
 *
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface InjectScope {
}
