package io.micronaut.kotlin.processing.aop.itfce

import io.micronaut.kotlin.processing.aop.simple.Mutating
import io.micronaut.kotlin.processing.aop.simple.CovariantClass

@Mutating("name")
interface InterfaceTypeLevel<A> {
    fun test(name: String): String
    fun test(name: String, age: Int): String
    fun test(): String
    fun testVoid(name: String)
    fun testVoid(name: String, age: Int)
    fun testBoolean(name: String): Boolean
    fun testBoolean(name: String, age: Int): Boolean
    fun testInt(name: String): Int
    fun testLong(name: String): Long
    fun testShort(name: String): Short
    fun testByte(name: String): Byte
    fun testDouble(name: String): Double
    fun testFloat(name: String): Float
    fun testChar(name: String): Char
    fun testByteArray(name: String, data: ByteArray): ByteArray
    fun <T : CharSequence> testGenericsWithExtends(name: T, age: Int): T
    fun <T> testListWithWildCardIn(name: T, p2: CovariantClass<in String>): CovariantClass<in String>
    fun <T> testListWithWildCardOut(name: T, p2: CovariantClass<out String>): CovariantClass<out String>
    fun testGenericsFromType(name: A, age: Int): A
}
