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
package io.micronaut.kotlin.processing.aop.simple

import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
open class SimpleClass<A : CharSequence?>(private val bar: Bar?) {
    var isPostConstructInvoked = false
        private set

    init {
        assert(bar != null)
    }

    @PostConstruct
    fun onCreate() {
        isPostConstructInvoked = true
    }

    @Mutating("name")
    open fun test(name: String): String {
        return "Name is $name"
    }

    @Mutating("age")
    open fun test(age: Int): String {
        return "Age is $age"
    }

    @Mutating("name")
    open fun test(name: String, age: Int): String {
        return "Name is $name and age is $age"
    }

    @Mutating("name")
    open fun test(): String {
        return "noargs"
    }

    @Mutating("name")
    open fun testVoid(name: String) {
        assert(name == "changed")
    }

    @Mutating("name")
    open fun testVoid(name: String, age: Int) {
        assert(name == "changed")
        assert(age == 10)
    }

    @Mutating("name")
    open fun testBoolean(name: String): Boolean {
        return name == "changed"
    }

    @Mutating("name")
    open fun testBoolean(name: String, age: Int): Boolean {
        assert(age == 10)
        return name == "changed"
    }

    @Mutating("name")
    open fun testInt(name: String): Int {
        return if (name == "changed") 1 else 0
    }

    @Mutating("age")
    open fun testInt(name: String, age: Int): Int {
        assert(name == "test")
        return age
    }

    @Mutating("name")
    open fun testLong(name: String): Long {
        return if (name == "changed") 1 else 0
    }

    @Mutating("age")
    open fun testLong(name: String, age: Int): Long {
        assert(name == "test")
        return age.toLong()
    }

    @Mutating("name")
    open fun testShort(name: String): Short {
        return (if (name == "changed") 1 else 0).toShort()
    }

    @Mutating("age")
    open fun testShort(name: String, age: Int): Short {
        assert(name == "test")
        return age.toShort()
    }

    @Mutating("name")
    open fun testByte(name: String): Byte {
        return (if (name == "changed") 1 else 0).toByte()
    }

    @Mutating("age")
    open fun testByte(name: String, age: Int): Byte {
        assert(name == "test")
        return age.toByte()
    }

    @Mutating("name")
    open fun testDouble(name: String): Double {
        return if (name == "changed") 1.0 else 0.0
    }

    @Mutating("age")
    open fun testDouble(name: String, age: Int): Double {
        assert(name == "test")
        return age.toDouble()
    }

    @Mutating("name")
    open fun testFloat(name: String): Float {
        return if (name == "changed") 1F else 0F
    }

    @Mutating("age")
    open fun testFloat(name: String, age: Int): Float {
        assert(name == "test")
        return age.toFloat()
    }

    @Mutating("name")
    open fun testChar(name: String): Char {
        return (if (name == "changed") 1 else 0).toChar()
    }

    @Mutating("age")
    open fun testChar(name: String, age: Int): Char {
        assert(name == "test")
        return age.toChar()
    }

    @Mutating("name")
    open fun testByteArray(name: String, data: ByteArray): ByteArray {
        assert(name == "changed")
        return data
    }

    @Mutating("name")
    open fun <T : CharSequence?> testGenericsWithExtends(name: T, age: Int): T {
        return "Name is $name" as T
    }

    @Mutating("name")
    open fun <T> testListWithWildCardIn(name: T, p2: CovariantClass<in String>): CovariantClass<in String> {
        return CovariantClass(name.toString())
    }

    @Mutating("name")
    open fun <T> testListWithWildCardOut(name: T, p2: CovariantClass<out String>): CovariantClass<out String> {
        return CovariantClass(name.toString())
    }

    @Mutating("name")
    open fun testGenericsFromType(name: A, age: Int): A {
        return "Name is $name" as A
    }

    @Invalid
    open fun invalidInterceptor() {
    }
}
