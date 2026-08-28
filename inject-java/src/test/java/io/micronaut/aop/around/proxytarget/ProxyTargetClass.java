package io.micronaut.aop.around.proxytarget;

import jakarta.annotation.PostConstruct;

import java.util.Collections;
import java.util.List;

public class ProxyTargetClass<A extends CharSequence> {

    public int lifeCycleCount = 0;
    public int invocationCount = 0;

    @PostConstruct
    void init() {
        lifeCycleCount++;
    }

    @Mutating("name")
    public String test(String name) {
        invocationCount++;
        return "Name is " + name;
    }

    @Mutating("age")
    public String test(int age) {
        return "Age is " + age;
    }

    @Mutating("name")
    public String test(String name, int age) {
        return "Name is " + name + " and age is " + age;
    }

    @Mutating("name")
    public String test() {
        return "noargs";
    }

    @Mutating("name")
    public void testVoid(String name) {
        assert name.equals("changed");
    }

    @Mutating("name")
    public void testVoid(String name, int age) {
        assert name.equals("changed");
        assert age == 10;
    }

    @Mutating("name")
    boolean testBoolean(String name) {
        return name.equals("changed");
    }

    @Mutating("name")
    boolean testBoolean(String name, int age) {
        assert age == 10;
        return name.equals("changed");
    }

    @Mutating("name")
    int testInt(String name) {
        return name.equals("changed") ? 1 : 0;
    }

    @Mutating("age")
    int testInt(String name, int age) {
        assert name.equals("test");
        return age;
    }

    @Mutating("name")
    long testLong(String name) {
        return name.equals("changed") ? 1 : 0;
    }

    @Mutating("age")
    long testLong(String name, int age) {
        assert name.equals("test");
        return age;
    }

    @Mutating("name")
    short testShort(String name) {
        return (short) (name.equals("changed") ? 1 : 0);
    }

    @Mutating("age")
    short testShort(String name, int age) {
        assert name.equals("test");
        return (short) age;
    }

    @Mutating("name")
    byte testByte(String name) {
        return (byte) (name.equals("changed") ? 1 : 0);
    }

    @Mutating("age")
    byte testByte(String name, int age) {
        assert name.equals("test");
        return (byte) age;
    }

    @Mutating("name")
    double testDouble(String name) {
        return name.equals("changed") ? 1 : 0;
    }

    @Mutating("age")
    double testDouble(String name, int age) {
        assert name.equals("test");
        return age;
    }

    @Mutating("name")
    float testFloat(String name) {
        return name.equals("changed") ? 1 : 0;
    }

    @Mutating("age")
    float testFloat(String name, int age) {
        assert name.equals("test");
        return age;
    }

    @Mutating("name")
    char testChar(String name) {
        return (char) (name.equals("changed") ? 1 : 0);
    }

    @Mutating("age")
    char testChar(String name, int age) {
        assert name.equals("test");
        return (char) age;
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
