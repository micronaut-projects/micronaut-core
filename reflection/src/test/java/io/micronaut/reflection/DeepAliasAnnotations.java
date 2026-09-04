package io.micronaut.reflection;

import io.micronaut.context.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A chain of composed annotations used to verify that an alias override reaches every level of a retained
 * stereotype tree.
 */
public final class DeepAliasAnnotations {

    private DeepAliasAnnotations() {
    }

    /**
     * The leaf of the composed annotation chain.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    @Contract
    public @interface DeepLimit {
        int min() default 0;
    }

    /**
     * The second intermediate annotation in the chain.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    @DeepLimit(min = 3)
    public @interface DeepC {
        @AliasFor(annotation = DeepLimit.class, member = "min", applyDefault = true)
        int min() default 3;
    }

    /**
     * The first intermediate annotation in the chain.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    @DeepC(min = 2)
    public @interface DeepB {
        @AliasFor(annotation = DeepC.class, member = "min", applyDefault = true)
        int min() default 2;
    }

    /**
     * The annotation placed on the test field, overriding every level below it.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
    @DeepB(min = 1)
    public @interface DeepA {
        @AliasFor(annotation = DeepB.class, member = "min", applyDefault = true)
        int shortest() default 1;
    }
}
