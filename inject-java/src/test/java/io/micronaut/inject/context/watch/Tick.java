package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Executable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Executable(processOnStartup = true)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tick {
    String value() default "";
}
