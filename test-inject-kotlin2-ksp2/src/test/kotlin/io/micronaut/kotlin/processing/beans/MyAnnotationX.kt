package io.micronaut.kotlin.processing.beans

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@MyAnnotation2(intArray3 = [1], stringArray4 = ["X"], boolArray4 = [false], myEnumArray4 = [MyEnum2.FOO])
annotation class MyAnnotationX

