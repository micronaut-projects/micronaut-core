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
package io.micronaut.reflection;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The shapes a type is read from and rendered back to, each as {@code java.lang.reflect} reports it.
 *
 * @param <K> A variable of the holder
 * @param <V> Another
 */
public class RoundTripTypes<K, V> {

    public List<? extends Number> extendsNumber;

    public List<Number> number;

    public Consumer<? super Book> superBook;

    public List<?> unbounded;

    public Map<K, V>[] variableMaps;

    public Map<String, Integer>[] stringMaps;

    public List<String>[][] matrix;

    public K[] keys;

    @SuppressWarnings("rawtypes")
    public List raw;

    public Outer<String>.Inner<Integer> inner;

    public Outer.Nested<Integer> nested;

    public <T extends Comparable<T> & Serializable> T several() {
        return null;
    }

    public <T extends Comparable<T> & Serializable> void arrayOfSeveral(T[] values) {
        // the signature is what is read
    }

    /**
     * A type that declares a variable.
     *
     * @param <A> The variable
     */
    public static class Outer<A> {

        /**
         * An inner class, whose owner carries the arguments of the outer.
         *
         * @param <B> The variable
         */
        public class Inner<B> {
        }

        /**
         * A nested class, whose owner is the bare outer.
         *
         * @param <B> The variable
         */
        public static class Nested<B> {
        }
    }

    /**
     * A type with a hierarchy to close over.
     */
    public static class Books extends ArrayList<Book> implements Comparable<Books> {

        @Override
        public int compareTo(Books other) {
            return 0;
        }
    }
}
