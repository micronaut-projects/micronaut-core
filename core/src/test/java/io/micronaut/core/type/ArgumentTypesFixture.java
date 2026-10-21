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
package io.micronaut.core.type;

import java.util.List;
import java.util.Map;

/**
 * Declarations whose reflected types the types rendered by {@link ArgumentTypes} are compared with.
 *
 * @param <T> A variable with a class and an interface bound naming it
 * @param <S> A variable with a single interface bound
 */
@SuppressWarnings("unused")
class ArgumentTypesFixture<T extends Number & Comparable<T>, S extends CharSequence> {

    List<String> list;
    Map<String, List<Integer>> map;
    List<String>[][] listMatrix;
    List<? extends Number> extendsNumber;
    List<? super Integer> superInteger;
    List<?> unbounded;
    Map.Entry<String, Integer> entry;
    Nested<String> nested;
    Outer<String>.Inner<Integer> inner;
    T variable;
    S chars;
    T[] variables;
    List<T> listOfT;

    static class Nested<X> {
    }

    static class Outer<O> {
        class Inner<I> {
        }
    }
}
