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
package io.micronaut.aop.lazy

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Mutating('name')
class LazyTargetClass {
    String test(String name) {
        return "Name is " + name
    }

    String test(String name, int age) {
        return "Name is "+name+" and age is " + age
    }


    String test() {
        return "noargs"
    }

    void testVoid(String name) {
        assert name.equals("changed")
    }

    void testVoid(String name, int age) {
        assert name.equals("changed")
        assert age == 10
    }

    boolean testBoolean(String name) {
        return name.equals("changed")
    }

    boolean testBoolean(String name, int age) {
        assert age == 10
        return name.equals("changed")
    }

    int testInt(String name) {
        return name.equals("changed") ? 1 : 0
    }

    long testLong(String name) {
        return name.equals("changed") ? 1 : 0
    }


    short testShort(String name) {
        return (short) (name.equals("changed") ? 1 : 0)
    }


    byte testByte(String name) {
        return (byte) (name.equals("changed") ? 1 : 0)
    }


    double testDouble(String name) {
        return name.equals("changed") ? 1 : 0
    }

    float testFloat(String name) {
        return name.equals("changed") ? 1 : 0
    }


    char testChar(String name) {
        return (char) (name.equals("changed") ? 1 : 0)
    }


    byte[] testByteArray(String name, byte[] data) {
        assert name.equals("changed")
        return data
    }

    def <T extends CharSequence> T testGenericsWithExtends(T name, int age) {
        return (T) ("Name is " + name)
    }

    def <T> List<? super String> testListWithWildCardSuper(T name, List<? super String> p2) {
        return Collections.singletonList(name.toString())
    }

    def <T> List<? extends String> testListWithWildCardExtends(T name, List<? extends String> p2) {
        return Collections.singletonList(name.toString())
    }

    Object testGenericsFromType(Object name, int age) {
        return  ("Name is " + name)
    }

}
