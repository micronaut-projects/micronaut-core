package io.micronaut.kotlin.processing.aop.factory;

import io.micronaut.kotlin.processing.aop.simple.CovariantClass

class InterfaceImpl: InterfaceClass<Any> {

    override fun test(): String {
        return "noargs"
    }

    override fun test(name: String): String {
        return "Name is $name"
    }

    override fun test(name: String, age: Int): String {
        return "Name is $name and age is $age"
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

    override fun testLong(name: String): Long {
        return if (name == "changed") 1 else 0
    }

    override fun testShort(name: String): Short {
        return (if (name == "changed") 1 else 0).toShort()
    }

    override fun testByte(name: String): Byte {
        return (if (name == "changed") 1 else 0).toByte()
    }

    override fun testDouble(name: String): Double {
        return if (name == "changed") 1.0 else 0.0
    }

    override fun testFloat(name: String): Float {
        return if (name == "changed") 1F else 0F
    }

    override fun testChar(name: String): Char {
        return (if (name == "changed") 1 else 0).toChar()
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

    override fun testGenericsFromType(name: Any, age: Int): Any {
        return "Name is $name"
    }
}
