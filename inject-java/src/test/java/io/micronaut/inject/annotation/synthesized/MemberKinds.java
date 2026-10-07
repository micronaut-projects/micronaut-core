package io.micronaut.inject.annotation.synthesized;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.TimeUnit;

/**
 * An annotation with a member of every kind, each with a default.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface MemberKinds {

    String string() default "a";

    int primitive() default 1;

    TimeUnit constant() default TimeUnit.SECONDS;

    Class<?> type() default Object.class;

    Nested nested() default @Nested("n");

    String[] strings() default {"a"};

    int[] primitives() default {1};

    TimeUnit[] constants() default {TimeUnit.SECONDS};

    Class<?>[] types() default {Object.class};

    Nested[] nesteds() default {@Nested("n")};

    /**
     * A nested annotation, with an enum member of its own.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @interface Nested {

        String value();

        TimeUnit unit() default TimeUnit.SECONDS;
    }
}
