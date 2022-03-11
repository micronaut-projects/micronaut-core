package io.micronaut.kotlin.processing.aop.itfce

import io.micronaut.kotlin.processing.aop.simple.CovariantClass
import jakarta.inject.Singleton

@Singleton
open class InterfaceImpl<A> : AbstractInterfaceImpl<A>() {

    override fun test(name: String): String {
        return "Name is $name"
    }

    override fun test(age: Int): String {
        return "Age is $age"
    }

    override fun test(): String {
        return "noargs"
    }

    override fun testVoid(name: String) {
        assert(name == "changed")
    }

    override fun testVoid(name: String, age: Int) {
        assert(name == "changed")
        assert(age == 10)
    }

    override fun testBoolean(name: String): Boolean {
        return name == "changed"
    }

    override fun testBoolean(name: String, age: Int): Boolean {
        assert(age == 10)
        return name == "changed"
    }

    override fun testInt(name: String): Int {
        return if (name == "changed") 1 else 0
    }

    override fun testInt(name: String, age: Int): Int {
        assert(name == "test")
        return age
    }

    override fun testLong(name: String): Long {
        return if (name == "changed") 1 else 0
    }

    override fun testLong(name: String, age: Int): Long {
        assert(name == "test")
        return age.toLong()
    }

    override fun testShort(name: String): Short {
        return (if (name == "changed") 1 else 0).toShort()
    }

    override fun testShort(name: String, age: Int): Short {
        assert(name == "test")
        return age.toShort()
    }

    override fun testByte(name: String): Byte {
        return (if (name == "changed") 1 else 0).toByte()
    }

    override fun testByte(name: String, age: Int): Byte {
        assert(name == "test")
        return age.toByte()
    }

    override fun testDouble(name: String): Double {
        return if (name == "changed") 1.0 else 0.0
    }

    override fun testDouble(name: String, age: Int): Double {
        assert(name == "test")
        return age.toDouble()
    }

    override fun testFloat(name: String): Float {
        return if (name == "changed") 1F else 0F
    }

    override fun testFloat(name: String, age: Int): Float {
        assert(name == "test")
        return age.toFloat()
    }

    override fun testChar(name: String): Char {
        return (if (name == "changed") 1 else 0).toChar()
    }

    override fun testChar(name: String, age: Int): Char {
        assert(name == "test")
        return age.toChar()
    }

    override fun testByteArray(name: String, data: ByteArray): ByteArray {
        assert(name == "changed")
        return data
    }

    override fun <T : CharSequence> testGenericsWithExtends(name: T, age: Int): T {
        return "Name is $name" as T
    }

    override fun <T> testListWithWildCardIn(name: T, p2: CovariantClass<in String>): CovariantClass<in String> {
        return CovariantClass(name.toString())
    }

    override fun <T> testListWithWildCardOut(name: T, p2: CovariantClass<out String>): CovariantClass<out String> {
        return CovariantClass(name.toString())
    }

    override fun testGenericsFromType(name: A, age: Int): A {
        return "Name is $name" as A
    }
}
