/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.aop.itfce

import io.micronaut.aop.interceptors.Mutating

/**
 * @author Graeme Rocher
 * @since 1.0
 */
interface InterfaceClass<A> {

    @Mutating("name")
    String test(String name)

    @Mutating("age")
    String test(int age)

    @Mutating("name")
    String test(String name, int age)

    @Mutating("name")
    String test()

    @Mutating("name")
    void testVoid(String name)

    @Mutating("name")
    void testVoid(String name, int age)

    @Mutating("name")
    boolean testBoolean(String name)

    @Mutating("name")
    boolean testBoolean(String name, int age)

    @Mutating("name")
    int testInt(String name)

    @Mutating("age")
    int testInt(String name, int age)

    @Mutating("name")
    long testLong(String name)

    @Mutating("age")
    long testLong(String name, int age)

    @Mutating("name")
    short testShort(String name)

    @Mutating("age")
    short testShort(String name, int age)

    @Mutating("name")
    byte testByte(String name)

    @Mutating("age")
    byte testByte(String name, int age)

    @Mutating("name")
    double testDouble(String name)

    @Mutating("age")
    double testDouble(String name, int age)

    @Mutating("name")
    float testFloat(String name)

    @Mutating("age")
    float testFloat(String name, int age)

    @Mutating("name")
    char testChar(String name)

    @Mutating("age")
    char testChar(String name, int age)

    @Mutating("name")
    byte[] testByteArray(String name, byte[] data)

    @Mutating("name")
    <T extends CharSequence> T testGenericsWithExtends(T name, int age)

    @Mutating("name")
    <T> List<? super String> testListWithWildCardSuper(T name, List<? super String> p2)

    @Mutating("name")
    <T> List<? extends String> testListWithWildCardExtends(T name, List<? extends String> p2)

    @Mutating("name")
    A testGenericsFromType(A name, int age)
}
