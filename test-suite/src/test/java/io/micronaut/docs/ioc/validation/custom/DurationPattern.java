package io.micronaut.docs.ioc.validation.custom;

// tag::imports[]
import javax.validation.Constraint;
import java.lang.annotation.*;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
// end::imports[]

// tag::class[]
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = { }) // <1>
public @interface DurationPattern {

    String message() default "invalid duration ({validatedValue})"; // <2>

    /**
     * Defines several constraints on the same element.
     */
    @Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
    @Retention(RUNTIME)
    @Documented
    @interface List {
        DurationPattern[] value(); // <3>
    }
}
// end::class[]
