package io.micronaut.context.env;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@interface ParentValue {

    String value() default "";

    ChildValue childValue() default @ChildValue();

    ChildValue[] childValues() default {};
}
