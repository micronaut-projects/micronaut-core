package io.micronaut.spring.tx;

import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.spring.tx.annotation.Transactional;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Transactional
@DefaultScope(Singleton.class)
public @interface MetaAnnotation {
}
