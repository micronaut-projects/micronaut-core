package io.micronaut.inject.visitor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes {@link GeneratedSourceVisitor} generate a source file for the annotated class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GenerateSource {

    /**
     * @return Whether the generated class is itself annotated so that a further class is generated from it
     */
    boolean chain() default false;

    /**
     * @return Extra source placed into the body of the generated class
     */
    String body() default "";
}
