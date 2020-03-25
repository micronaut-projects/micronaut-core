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
package io.micronaut.aop.simple

import io.micronaut.aop.interceptors.Mutating

import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
class SimpleGroovyClass<A extends CharSequence> {

    Bar bar

    SimpleGroovyClass(Bar bar) {
        this.bar = bar
        assert bar != null
    }

    @Mutating('name')
    String test(String name) {
        "Name is $name"
    }

    @Mutating('age')
    String test(int age) {
        "Age is $age"
    }

    @Mutating('name')
    String test(String name, int age) {
        "Name is $name and age is $age"
    }

    @Mutating('name')
    String test() {
        "noargs"
    }

    @Mutating('name')
    void testVoid(String name) {
        assert name == 'changed'
        "Name is $name"
    }

    @Mutating('name')
    void testVoid(String name, int age) {
        assert name == 'changed'
        assert age == 10
        "Name is $name"
    }

    @Mutating('name')
    boolean testBoolean(String name) {
        name == 'changed'
    }

    @Mutating('name')
    boolean testBoolean(String name, int age) {
        assert age == 10
        return name == 'changed'
    }

    @Mutating('name')
    int testInt(String name) {
        name == 'changed' ? 1 : 0
    }

    @Mutating('age')
    int testInt(String name, int age) {
        assert name == 'test'
        return age
    }

    @Mutating('name')
    long testLong(String name) {
        name == 'changed' ? 1 : 0
    }

    @Mutating('age')
    long testLong(String name, int age) {
        assert name == 'test'
        return age
    }

    @Mutating('name')
    short testShort(String name) {
        name == 'changed' ? 1 : 0
    }

    @Mutating('age')
    short testShort(String name, int age) {
        assert name == 'test'
        return age
    }

    @Mutating('name')
    byte testByte(String name) {
        name == 'changed' ? 1 : 0
    }

    @Mutating('age')
    byte testByte(String name, int age) {
        assert name == 'test'
        return age
    }

    @Mutating('name')
    double testDouble(String name) {
        name == 'changed' ? 1 : 0
    }

    @Mutating('age')
    double testDouble(String name, int age) {
        assert name == 'test'
        return age
    }

    @Mutating('name')
    float testFloat(String name) {
        name == 'changed' ? 1 : 0
    }

    @Mutating('age')
    float testFloat(String name, int age) {
        assert name == 'test'
        return age
    }

    @Mutating('name')
    char testChar(String name) {
        name == 'changed' ? 1 : 0
    }

    @Mutating('age')
    char testChar(String name, int age) {
        assert name == 'test'
        return age
    }

    @Mutating("name")
    byte[] testByteArray(String name, byte[] data) {
        assert name.equals("changed");
        return data;
    }

    @Mutating("name")
    <T extends CharSequence> T testGenericsWithExtends(T name, int age) {
        return (T) ("Name is " + name);
    }

    @Mutating("name")
    <T> List<? super String> testListWithWildCardSuper(T name, List<? super String> p2) {
        return Collections.singletonList(name.toString());
    }

    @Mutating("name")
    <T> List<? extends String> testListWithWildCardExtends(T name, List<? extends String> p2) {
        return Collections.singletonList(name.toString());
    }

    @Mutating("name")
    A testGenericsFromType(A name, int age) {
        return (A) ("Name is " + name);
    }
}
