package io.micronaut.inject.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Repeatable(Tags.class)
@Stereo(kind = "tag")
public @interface Tag {
    String value();

    int priority() default 1;

    Class<?> type() default Object.class;

    Level level() default Level.LOW;

    Stereo nested() default @Stereo;
}
