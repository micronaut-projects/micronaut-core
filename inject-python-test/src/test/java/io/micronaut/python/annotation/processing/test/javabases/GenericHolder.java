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
 * A generic base whose abstract method takes and returns the type variable.
 *
 * @param <T> The value type
 */
public abstract class GenericHolder<T> {

    private T value;

    protected abstract T transform(T value);

    public T apply(T value) {
        this.value = transform(value);
        return this.value;
    }

    public T value() {
        return value;
    }
}
