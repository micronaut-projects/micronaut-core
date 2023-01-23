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

import io.micronaut.kotlin.processing.aop.simple.CovariantClass

/**
 * @author Graeme Rocher
 * @since 1.0
 */
interface InterfaceClass<A> {
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
