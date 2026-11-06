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

import java.util.List;

/**
 * A concrete Java class with state, an overridable method, a final method, a protected hook,
 * protected methods declaring a type variable of their own and overloads, extended by Python
 * classes in the tests.
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

    protected <T> String bindType(Class<T> type) {
        return "bind:" + type.getSimpleName();
    }

    protected <T> String tag(T value) {
        return "tag:" + value;
    }

    public <T> T[] packAll(T[] values) {
        return values;
    }

    public <T> T[][] packGrid(T[][] values) {
        return values;
    }

    public <T> String packEach(String[][] rows, T tag) {
        return "rows:" + rows.length + tag;
    }

    public <T extends Runnable & Comparable<T>> String runBoth(T value) {
        return "both:" + value;
    }

    public <U extends Runnable & Comparable<U>, T extends U> String runNarrowed(T value) {
        return "narrowed:" + value;
    }

    public <T extends Comparable<T>> String sortLists(List<T>[] lists) {
        return "lists:" + lists.length;
    }

    public <T extends Runnable & Comparable<T>> String runLists(List<T>[] lists) {
        return "runnable-lists:" + lists.length;
    }

    public String mayThrowAnything() throws Throwable {
        return "thrown";
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
