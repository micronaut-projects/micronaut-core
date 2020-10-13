package org.atinject.tck.auto.events;

import io.micronaut.aop.Adapter;
import io.micronaut.core.annotation.Indexed;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Target({ANNOTATION_TYPE, METHOD})
@Adapter(EventHandlerMultipleArguments.class)
@Indexed(EventHandlerMultipleArguments.class)
public @interface EventHandler {

}
