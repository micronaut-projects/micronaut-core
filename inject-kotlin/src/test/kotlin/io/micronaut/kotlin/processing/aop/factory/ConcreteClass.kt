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
package io.micronaut.kotlin.processing.aop.factory

import io.micronaut.core.annotation.Creator
import io.micronaut.kotlin.processing.aop.simple.CovariantClass
import io.micronaut.kotlin.processing.aop.simple.Mutating

/**
 * @author Graeme Rocher
 * @since 1.0
 */
open class ConcreteClass {
    private val anotherClass: AnotherClass?

    @Creator
    constructor() {
        anotherClass = null
    }

    constructor(anotherClass: AnotherClass?) {
        this.anotherClass = anotherClass
    }

    open fun test(name: String): String {
        return "Name is $name"
    }

    open fun test(name: String, age: Int): String {
        return "Name is $name and age is $age"
    }

    open fun test(): String {
        return "noargs"
    }

    open fun testVoid(name: String) {
        assert(name == "changed")
    }

    open fun testVoid(name: String, age: Int) {
        assert(name == "changed")
        assert(age == 10)
    }

    open fun testBoolean(name: String): Boolean {
        return name == "changed"
    }

    open fun testBoolean(name: String, age: Int): Boolean {
        assert(age == 10)
        return name == "changed"
    }

    open fun testInt(name: String): Int {
        return if (name == "changed") 1 else 0
    }

    open fun testLong(name: String): Long {
        return if (name == "changed") 1 else 0
    }

    open fun testShort(name: String): Short {
        return (if (name == "changed") 1 else 0).toShort()
    }

    open fun testByte(name: String): Byte {
        return (if (name == "changed") 1 else 0).toByte()
    }

    open fun testDouble(name: String): Double {
        return if (name == "changed") 1.0 else 0.0
    }

    open fun testFloat(name: String): Float {
        return if (name == "changed") 1F else 0F
    }

    open fun testChar(name: String): Char {
        return (if (name == "changed") 1 else 0).toChar()
    }

    open fun testByteArray(name: String, data: ByteArray): ByteArray {
        assert(name == "changed")
        return data
    }

    open fun <T : CharSequence?> testGenericsWithExtends(name: T, age: Int): T {
        return "Name is $name" as T
    }

    open fun <T> testListWithWildCardIn(name: T, p2: CovariantClass<in String>): CovariantClass<in String> {
        return CovariantClass(name.toString())
    }

    open fun <T> testListWithWildCardOut(name: T, p2: CovariantClass<out String>): CovariantClass<out String> {
        return CovariantClass(name.toString())
    }

    open fun testGenericsFromType(name: Any, age: Int): Any {
        return "Name is $name"
    }
}
