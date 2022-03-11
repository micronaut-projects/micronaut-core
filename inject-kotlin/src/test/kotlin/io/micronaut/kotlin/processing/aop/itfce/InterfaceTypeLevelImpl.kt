/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.kotlin.processing.aop.itfce

import io.micronaut.kotlin.processing.aop.simple.CovariantClass
import jakarta.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
open class InterfaceTypeLevelImpl : AbstractInterfaceTypeLevel<Any>() {

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
