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
package io.micronaut.python.annotation.processing.test.defaults;

import io.micronaut.core.order.Ordered;

import java.util.List;

/**
 * A Java interface with default methods, implemented by Python classes in the tests.
 */
public interface Describable extends Ordered {

    String name();

    default String describe() {
        return "d:" + name();
    }

    default String describeWith(String prefix, int times) {
        return prefix.repeat(times) + name();
    }

    default List<String> describeAll(List<String> suffixes) {
        return suffixes.stream().map(suffix -> name() + suffix).toList();
    }

    default Describable self() {
        return this;
    }
}
