package io.micronaut.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation with a member of every kind a member may have, and a default for each, so that the metadata
 * built either way is compared over every conversion the builders do.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.CONSTRUCTOR})
public @interface Every {
    byte aByte() default 1;

    short aShort() default 2;

    int anInt() default 3;

    long aLong() default 4L;

    float aFloat() default 5.5f;

    double aDouble() default 6.5d;

    char aChar() default 'x';

    boolean aBoolean() default true;

    String aString() default "one";

    Class<?> aClass() default String.class;

    Level anEnum() default Level.LOW;

    Stereo anAnnotation() default @Stereo(kind = "inner");

    byte[] bytes() default {1, 2};

    short[] shorts() default {3, 4};

    int[] ints() default {5, 6};

    long[] longs() default {7L, 8L};

    float[] floats() default {9.5f};

    double[] doubles() default {10.5d};

    char[] chars() default {'a', 'b'};

    boolean[] booleans() default {true, false};

    String[] strings() default {"a", "b"};

    Class<?>[] classes() default {String.class, Integer.class};

    Level[] enums() default {Level.LOW, Level.HIGH};

    Stereo[] annotations() default {@Stereo(kind = "a"), @Stereo(kind = "b")};
}
