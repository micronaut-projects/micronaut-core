package io.micronaut.inject.annotation

@interface MyAnnotation {

    int num() default 10;

    String value() default "";

    boolean bool() default false;

    MyEnum2 myEnum() default MyEnum2.ABC;

    int[] intArray1() default [];

    int[] intArray2() default [1, 2, 3];

    int[] intArray3();

    String[] stringArray1() default [];

    String[] stringArray2() default [""];

    String[] stringArray3() default ["A"];

    String[] stringArray4();

    boolean[] boolArray1() default [];

    boolean[] boolArray2() default [true];

    boolean[] boolArray3() default [false];

    boolean[] boolArray4();

    MyEnum2[] myEnumArray1() default [];

    MyEnum2[] myEnumArray2() default [MyEnum2.ABC];

    MyEnum2[] myEnumArray3() default [MyEnum2.FOO, MyEnum2.BAR];

    MyEnum2[] myEnumArray4();

    Class[] classesArray1() default [];

    Class[] classesArray2() default [String.class];

    Class[] classesArray3() default [String[].class];

    Class[] classesArray4() default [String[][].class];

    MyAnnotation3[] annotationsArray1() default [];

//    Default annotation values are crashing Groovy compiler
//    MyAnnotation3[] annotationsArray2() default [@MyAnnotation3("foo"), @MyAnnotation3("bar")];

}
