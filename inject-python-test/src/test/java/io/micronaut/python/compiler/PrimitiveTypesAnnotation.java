package io.micronaut.python.compiler;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface PrimitiveTypesAnnotation {
    boolean booleanValue() default true;
    byte byteValue() default 42;
    char charValue() default 'A';
    double doubleValue() default 3.14;
    float floatValue() default 2.71f;
    int intValue() default 100;
    long longValue() default 1000L;
    short shortValue() default 10;

    boolean[] booleanArray() default {true, false};
    byte[] byteArray() default {1, 2, 3};
    char[] charArray() default {'A', 'B', 'C'};
    double[] doubleArray() default {1.1, 2.2};
    float[] floatArray() default {1.1f, 2.2f};
    int[] intArray() default {10, 20, 30};
    long[] longArray() default {100L, 200L};
    short[] shortArray() default {1, 2, 3};

    String stringValue() default "test";
    String[] stringArray() default {"hello", "world"};

    Class<?> classValue() default String.class;
    Class<?>[] classArray() default {String.class, Integer.class};
}
