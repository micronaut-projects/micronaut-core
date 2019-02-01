package io.micronaut.inject.context.processor;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.DefaultScope;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Bean
@DefaultScope(Singleton.class)
public @interface ProcessedAnnotation {
}
