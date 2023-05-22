package io.micronaut.kotlin.processing.aop.itfce

import io.micronaut.kotlin.processing.aop.simple.CovariantClass
import io.micronaut.kotlin.processing.aop.simple.Mutating

interface InterfaceClass<A> {

    @Mutating("name")
    fun test(name: String): String

    @Mutating("age")
    fun test(age: Int): String

    @Mutating("name")
    fun test(name: String, age: Int): String

    @Mutating("name")
    fun test(): String

    @Mutating("name")
    fun testVoid(name: String)

    @Mutating("name")
    fun testVoid(name: String, age: Int)

    @Mutating("name")
    fun testBoolean(name: String): Boolean

    @Mutating("name")
    fun testBoolean(name: String, age: Int): Boolean

    @Mutating("name")
    fun testInt(name: String): Int

    @Mutating("age")
    fun testInt(name: String, age: Int): Int

    @Mutating("name")
    fun testLong(name: String): Long

    @Mutating("age")
    fun testLong(name: String, age: Int): Long

    @Mutating("name")
    fun testShort(name: String): Short

    @Mutating("age")
    fun testShort(name: String, age: Int): Short

    @Mutating("name")
    fun testByte(name: String): Byte

    @Mutating("age")
    fun testByte(name: String, age: Int): Byte

    @Mutating("name")
    fun testDouble(name: String): Double

    @Mutating("age")
    fun testDouble(name: String, age: Int): Double

    @Mutating("name")
    fun testFloat(name: String): Float

    @Mutating("age")
    fun testFloat(name: String, age: Int): Float

    @Mutating("name")
    fun testChar(name: String): Char

    @Mutating("age")
    fun testChar(name: String, age: Int): Char

    @Mutating("name")
    fun testByteArray(name: String, data: ByteArray): ByteArray

    @Mutating("name")
    fun <T : CharSequence> testGenericsWithExtends(name: T, age: Int): T

    @Mutating("name")
    fun <T> testListWithWildCardIn(name: T, p2: CovariantClass<in String>): CovariantClass<in String>

    @Mutating("name")
    fun <T> testListWithWildCardOut(name: T, p2: CovariantClass<out String>): CovariantClass<out String>

    @Mutating("name")
    fun testGenericsFromType(name: A, age: Int): A
}
