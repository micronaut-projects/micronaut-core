package io.micronaut.kotlin.processing.beans

import kotlin.reflect.KClass

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class MyAnnotation2(
    val num: Int = 10,
    val value: String = "",
    val bool: Boolean = false,
    val ann: MyAnnotation3 = MyAnnotation3("foo"),
    val myEnum: MyEnum2 = MyEnum2.ABC,
    val intArray1: IntArray = [],
    val intArray2: IntArray = [1, 2, 3],
    val intArray3: IntArray,
    val stringArray1: Array<String> = [],
    val stringArray2: Array<String> = [""],
    val stringArray3: Array<String> = ["A"],
    val stringArray4: Array<String>,
    val boolArray1: BooleanArray = [],
    val boolArray2: BooleanArray = [true],
    val boolArray3: BooleanArray = [false],
    val boolArray4: BooleanArray,
    val myEnumArray1: Array<MyEnum2> = [],
    val myEnumArray2: Array<MyEnum2> = [MyEnum2.ABC],
    val myEnumArray3: Array<MyEnum2> = [MyEnum2.FOO, MyEnum2.BAR],
    val myEnumArray4: Array<MyEnum2>,
    val classesArray1: Array<KClass<*>> = [],
    val classesArray2: Array<KClass<*>> = [String::class],
    val annotationsArray1: Array<MyAnnotation3> = [],
    val annotationsArray2: Array<MyAnnotation3> = [MyAnnotation3(
        "foo"
    ), MyAnnotation3("bar")]
)


@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class MyAnnotation3(val value: String = "")

enum class MyEnum2 {
    FOO, BAR, ABC
}

