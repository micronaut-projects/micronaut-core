package io.micronaut.inject.annotation;

import io.micronaut.context.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE})
public @interface MyAnnotation2Aliases {

    int[] intArray1() default {};

    int[] intArray2() default {};

    @AliasFor(member = "intArray1")
    int[] intArray1Alias();

    @AliasFor(member = "intArray2")
    int[] intArray2Alias();

    String[] stringArray1() default {};

    String[] stringArray2() default {};

    String[] stringArray3() default {};

    @AliasFor(member = "stringArray1")
    String[] stringArray1Alias();

    @AliasFor(member = "stringArray2")
    String[] stringArray2Alias();

    @AliasFor(member = "stringArray3")
    String[] stringArray3Alias();

    MyEnum2[] myEnumArray1() default {};

    MyEnum2[] myEnumArray2() default {};

    MyEnum2[] myEnumArray3() default {};

    @AliasFor(member = "myEnumArray1")
    MyEnum2[] myEnumArray1Alias();

    @AliasFor(member = "myEnumArray2")
    MyEnum2[] myEnumArray2Alias();

    @AliasFor(member = "myEnumArray3")
    MyEnum2[] myEnumArray3Alias();

    Class[] classesArray1() default {};

    Class[] classesArray2() default {};

    @AliasFor(member = "classesArray1")
    Class[] classesArray1Alias();

    @AliasFor(member = "classesArray2")
    Class[] classesArray2Alias();

    MyAnnotation3 ann() default @MyAnnotation3("default");

    @AliasFor(member = "ann")
    MyAnnotation3 annAlias();

    MyAnnotation3[] annotationsArray1() default {};

    MyAnnotation3[] annotationsArray2() default {};

    @AliasFor(member = "annotationsArray1")
    MyAnnotation3[] annotationsArray1Alias();

    @AliasFor(member = "annotationsArray2")
    MyAnnotation3[] annotationsArray2Alias();

}
