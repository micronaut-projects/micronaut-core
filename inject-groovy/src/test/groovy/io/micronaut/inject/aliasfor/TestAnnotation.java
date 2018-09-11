package io.micronaut.inject.aliasfor;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Executable;

import javax.inject.Named;
import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Singleton
@Executable
public @interface TestAnnotation {
    /**
     * @return An optional ID of the function which may or may not be used depending on the target platform
     */
    @AliasFor(annotation = Named.class, member = "value")
    String value() default "";

}
