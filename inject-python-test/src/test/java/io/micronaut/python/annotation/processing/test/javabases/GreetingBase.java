/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.python.annotation.processing.test.javabases;

/**
 * A concrete Java class with state, an overridable method, a final method, a protected hook and
 * overloads, extended by Python classes in the tests.
 */
public class GreetingBase {

    private final String name;
    private int count;

    public GreetingBase(String name, int count) {
        this.name = name;
        this.count = count;
    }

    public String greet() {
        return "Hello " + name + " x" + count;
    }

    public String describe() {
        return "describe:" + greet();
    }

    public final String finalGreeting() {
        return "final:" + name;
    }

    protected String protectedHook() {
        return "hook:" + name;
    }

    public int increment() {
        return ++count;
    }

    public String getName() {
        return name;
    }

    public String join(String value) {
        return value;
    }

    public String join(String value, int times) {
        return value.repeat(times);
    }

    public String join(int times) {
        return "*".repeat(times);
    }

    public GreetingBase self() {
        return this;
    }

    public static String staticHelper(String value) {
        return "static:" + value;
    }
}
