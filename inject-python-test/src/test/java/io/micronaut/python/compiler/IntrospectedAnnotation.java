package io.micronaut.python.compiler;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface IntrospectedAnnotation {
    BuilderAnnotation builder() default @BuilderAnnotation;
}

@Retention(RetentionPolicy.RUNTIME)
@interface BuilderAnnotation {
    String style() default "default";
}
