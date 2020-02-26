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
package io.micronaut.aop.simple;

import java.util.Collections;
import java.util.List;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Mutating("name")
public class AnotherClass<A> {

    // protected methods not proxied
    protected String testProtected(String name) {
        return "Name is " + name;
    }

    // protected methods not proxied
    private String testPrivate(String name) {
        return "Name is " + name;
    }

    public String test(String name) {
        return "Name is " + name;
    }

    @Mutating("age")
    public String test(int age) {
        return "Age is " + age;
    }

    public String test(String name, int age) {
        return "Name is "+name+" and age is " + age;
    }

    public String test() {
        return "noargs";
    }

    public void testVoid(String name) {
        assert name.equals("changed");
    }

    public void testVoid(String name, int age) {
        assert name.equals("changed");
        assert age == 10;
    }

    public boolean testBoolean(String name) {
        return name.equals("changed");
    }

    public boolean testBoolean(String name, int age) {
        assert age == 10;
        return name.equals("changed");
    }

    public int testInt(String name) {
        return name.equals("changed") ? 1 : 0;
    }

    @Mutating("age")
    public int testInt(String name, int age) {
        assert name.equals("test");
        return age;
    }

    public long testLong(String name) {
        return name.equals("changed") ? 1 : 0;
    }

    @Mutating("age")
    public long testLong(String name, int age) {
        assert name.equals("test");
        return age;
    }

    public short testShort(String name) {
        return (short) (name.equals("changed") ? 1 : 0);
    }

    @Mutating("age")
    public short testShort(String name, int age) {
        assert name.equals("test");
        return (short) age;
    }

    public byte testByte(String name) {
        return (byte) (name.equals("changed") ? 1 : 0);
    }

    @Mutating("age")
    public byte testByte(String name, int age) {
        assert name.equals("test");
        return (byte) age;
    }

    public double testDouble(String name) {
        return name.equals("changed") ? 1 : 0;
    }

    @Mutating("age")
    public double testDouble(String name, int age) {
        assert name.equals("test");
        return age;
    }

    public float testFloat(String name) {
        return name.equals("changed") ? 1 : 0;
    }

    @Mutating("age")
    public float testFloat(String name, int age) {
        assert name.equals("test");
        return age;
    }

    public char testChar(String name) {
        return (char) (name.equals("changed") ? 1 : 0);
    }

    @Mutating("age")
    public char testChar(String name, int age) {
        assert name.equals("test");
        return (char) age;
    }

    public byte[] testByteArray(String name, byte[] data) {
        assert name.equals("changed");
        return data;
    }

    public <T extends CharSequence> T testGenericsWithExtends(T name, int age) {
        return (T) ("Name is " + name);
    }

    public <T> List<? super String> testListWithWildCardSuper(T name, List<? super String> p2) {
        return Collections.singletonList(name.toString());
    }

    public <T> List<? extends String> testListWithWildCardExtends(T name, List<? extends String> p2) {
        return Collections.singletonList(name.toString());
    }

    public A testGenericsFromType(A name, int age) {
        return (A) ("Name is " + name);
    }
}
