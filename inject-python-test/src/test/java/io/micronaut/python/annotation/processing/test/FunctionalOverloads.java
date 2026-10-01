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
package io.micronaut.python.annotation.processing.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A Java bean whose methods are overloaded on functional interfaces of different arities,
 * called from Python with lambdas.
 */
public class FunctionalOverloads {

    private final List<Consumer<String>> consumers = new ArrayList<>();
    private final List<CustomCallback> callbacks = new ArrayList<>();

    public String apply(Function<String, String> function) {
        return "function:" + function.apply("a");
    }

    public String apply(BiFunction<String, String, String> function) {
        return "bifunction:" + function.apply("a", "b");
    }

    public String check(Predicate<String> predicate) {
        return "predicate:" + predicate.test("x");
    }

    public String check(BiPredicate<String, Integer> predicate) {
        return "bipredicate:" + predicate.test("x", 3);
    }

    public String run(Runnable runnable) {
        runnable.run();
        return "runnable";
    }

    public String run(Supplier<String> supplier) {
        return "supplier:" + supplier.get();
    }

    public void register(Consumer<String> consumer) {
        consumers.add(consumer);
    }

    public Consumer<String> registered() {
        return consumers.isEmpty() ? null : consumers.get(consumers.size() - 1);
    }

    public List<Consumer<String>> registeredConsumers() {
        return consumers;
    }

    public String callback(CustomCallback callback) {
        callbacks.add(callback);
        return "custom:" + callback.call("x");
    }

    public String callback(CustomBiCallback callback) {
        return "custombi:" + callback.call("x", 2);
    }

    public List<CustomCallback> callbacks() {
        return callbacks;
    }

    @FunctionalInterface
    public interface CustomCallback {
        String call(String value);
    }

    @FunctionalInterface
    public interface CustomBiCallback {
        String call(String value, int count);
    }
}
