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
 * A generic base naming its class type variable in its methods, with and without a type variable of their
 * own, in the shape of a repository: {@code super.save(S)} takes what the extended type makes of {@code E}.
 *
 * @param <E> The entity type
 */
public class GenericRepository<E> {

    public <S extends E> S save(S entity) {
        return entity;
    }

    public <T> String saveAll(E[] entities, T tag) {
        return "saveAll:" + entities.length + tag;
    }

    public <T> String saveTagged(E entity, T tag) {
        return "saveTagged:" + entity + tag;
    }

    public String add(E entity) {
        return "add:" + entity;
    }

    public String addAll(E[] entities) {
        return "addAll:" + entities.length;
    }

    /**
     * Gives the type variable its type argument in Java.
     */
    public static class Names extends GenericRepository<String> {
    }

    /**
     * Passes the type variable on, under the name a method of the base declares for a variable of its own.
     *
     * @param <T> The entity type
     */
    public static class Passing<T> extends GenericRepository<T> {
    }

    /**
     * A bounded type variable of a nested class, which a raw base leaves at its bound.
     *
     * @param <N> The entity type
     */
    public static class Numbers<N extends Number> {

        public <T> String saveAll(N[] entities, T tag) {
            return "numbers:" + entities.length + tag;
        }

        public <S extends N> String save(S entity) {
            return "number:" + entity;
        }

        public String add(N entity) {
            return "added:" + entity;
        }
    }

    /**
     * Gives the type variable of the nested class its type argument in Java.
     */
    public static class Integers extends Numbers<Integer> {
    }
}
