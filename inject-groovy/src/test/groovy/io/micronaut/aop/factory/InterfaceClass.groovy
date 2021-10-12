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
package io.micronaut.aop.factory
/**
 * @author Graeme Rocher
 * @since 1.0
 */
interface InterfaceClass<A> {


    String test(String name)

    String test(String name, int age)


    String test()


    void testVoid(String name)


    void testVoid(String name, int age)


    boolean testBoolean(String name)


    boolean testBoolean(String name, int age)


    int testInt(String name)


    long testLong(String name)


    short testShort(String name)


    byte testByte(String name)


    double testDouble(String name)


    float testFloat(String name)

    char testChar(String name)


    byte[] testByteArray(String name, byte[] data)


    def <T extends CharSequence> T testGenericsWithExtends(T name, int age)


    def <T> List<? super String> testListWithWildCardSuper(T name, List<? super String> p2)


    def <T> List<? extends String> testListWithWildCardExtends(T name, List<? extends String> p2)


    A testGenericsFromType(A name, int age)
}
