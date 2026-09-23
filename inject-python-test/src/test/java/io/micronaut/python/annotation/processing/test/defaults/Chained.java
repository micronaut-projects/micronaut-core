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

/**
 * An interface whose default methods call each other and cast {@code this} to another interface.
 */
public interface Chained {

    String name();

    default String inner() {
        return "j-inner:" + name();
    }

    default String outer() {
        return "o:" + inner();
    }

    default String tagged() {
        return ((Tagged) this).tag() + ":" + name();
    }
}
