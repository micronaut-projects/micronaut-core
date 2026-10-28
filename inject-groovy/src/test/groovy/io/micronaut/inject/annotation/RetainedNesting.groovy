package io.micronaut.inject.annotation

import io.micronaut.context.annotation.AliasFor

import java.lang.annotation.Repeatable
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/*
 * Annotations for the nested retention test of RetainableSpec, compiled ahead of the sources under test because
 * the Groovy frontend does not resolve the annotations of an annotation type declared in the same source.
 */

@Limit(min = 1)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RetainedBoundedList)
@interface RetainedBounded {
    @AliasFor(annotation = Limit, member = "min", applyDefault = true)
    int min() default 1
}

@Retention(RetentionPolicy.RUNTIME)
@interface RetainedBoundedList {
    RetainedBounded[] value()
}

@RetainedBounded(min = 2)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RetainedRangeList)
@interface RetainedRange {
    @AliasFor(annotation = RetainedBounded, member = "min", applyDefault = true)
    int from() default 2
}

@Retention(RetentionPolicy.RUNTIME)
@interface RetainedRangeList {
    RetainedRange[] value()
}

@Retention(RetentionPolicy.RUNTIME)
@interface RetainedGroup {
    RetainedRange[] value()
}

@Retention(RetentionPolicy.RUNTIME)
@interface RetainedHolder {
    RetainedRange[] ranges() default []

    RetainedBounded single()

    RetainedGroup[] groups() default []

    RetainedRangeList list()
}

@Limit(min = 1)
@Retention(RetentionPolicy.RUNTIME)
@interface RetainedBundle {
    @AliasFor(annotation = Limit, member = "min", applyDefault = true)
    int min() default 1

    /**
     * A container the bundled annotation does not declare as repeatable.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        RetainedBundle[] value()
    }
}
