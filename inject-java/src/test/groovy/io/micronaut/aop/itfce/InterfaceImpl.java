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
package io.micronaut.aop.itfce;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class InterfaceImpl<A> extends AbstractInterfaceImpl<A> {
    @Override
    public String test(String name) {
        return "Name is " + name;
    }

    @Override
    public String test(int age) {
        return "Age is " + age;
    }


    @Override
    public String test() {
        return "noargs";
    }

    @Override
    public void testVoid(String name) {
        assert name.equals("changed");
    }

    @Override
    public void testVoid(String name, int age) {
        assert name.equals("changed");
        assert age == 10;
    }

    @Override
    public boolean testBoolean(String name) {
        return name.equals("changed");
    }

    @Override
    public boolean testBoolean(String name, int age) {
        assert age == 10;
        return name.equals("changed");
    }

    @Override
    public int testInt(String name) {
        return name.equals("changed") ? 1 : 0;
    }

    @Override
    public int testInt(String name, int age) {
        assert name.equals("test");
        return age;
    }

    @Override
    public long testLong(String name) {
        return name.equals("changed") ? 1 : 0;
    }

    @Override
    public long testLong(String name, int age) {
        assert name.equals("test");
        return age;
    }

    @Override
    public short testShort(String name) {
        return (short) (name.equals("changed") ? 1 : 0);
    }

    @Override
    public short testShort(String name, int age) {
        assert name.equals("test");
        return (short) age;
    }

    @Override
    public byte testByte(String name) {
        return (byte) (name.equals("changed") ? 1 : 0);
    }

    @Override
    public byte testByte(String name, int age) {
        assert name.equals("test");
        return (byte) age;
    }

    @Override
    public double testDouble(String name) {
        return name.equals("changed") ? 1 : 0;
    }

    @Override
    public double testDouble(String name, int age) {
        assert name.equals("test");
        return age;
    }

    @Override
    public float testFloat(String name) {
        return name.equals("changed") ? 1 : 0;
    }

    @Override
    public float testFloat(String name, int age) {
        assert name.equals("test");
        return age;
    }

    @Override
    public char testChar(String name) {
        return (char) (name.equals("changed") ? 1 : 0);
    }

    @Override
    public char testChar(String name, int age) {
        assert name.equals("test");
        return (char) age;
    }

    @Override
    public byte[] testByteArray(String name, byte[] data) {
        assert name.equals("changed");
        return data;
    }

    @Override
    public <T extends CharSequence> T testGenericsWithExtends(T name, int age) {
        return (T) ("Name is " + name);
    }

    @Override
    public <T> List<? super String> testListWithWildCardSuper(T name, List<? super String> p2) {
        return Collections.singletonList(name.toString());
    }

    @Override
    public <T> List<? extends String> testListWithWildCardExtends(T name, List<? extends String> p2) {
        return Collections.singletonList(name.toString());
    }

    @Override
    public A testGenericsFromType(A name, int age) {
        return (A) ("Name is " + name);
    }
}
