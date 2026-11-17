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
 * An overload pair of the same erasure, with and without a type variable. One class cannot declare both
 * ({@code name clash: <T>bind(Class<T>) and bind(Class) have the same erasure}), and a subclass overriding one
 * replaces it; a class and an interface default are how the two reach a base together.
 */
public class BindingOverloads {

    public <T> String bind(Class<T> type) {
        return "generic:" + type.getSimpleName();
    }

    /**
     * The overload without a type variable.
     */
    public interface RawBinding {

        @SuppressWarnings("rawtypes")
        default String bind(Class type) {
            return "raw:" + type.getSimpleName();
        }
    }

    /**
     * Inherits both overloads.
     */
    public static class Both extends BindingOverloads implements RawBinding {
    }
}
