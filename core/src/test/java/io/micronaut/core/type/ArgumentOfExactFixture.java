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
import java.util.function.Consumer;

/**
 * Declarations whose reflected types {@link Argument#ofExact(java.lang.reflect.Type)} reads.
 *
 * @param <T> A variable with a class and an interface bound naming it
 * @param <E> A variable bounded by a type naming it
 * @param <K> An unbounded variable
 */
@SuppressWarnings({"unused", "rawtypes"})
class ArgumentOfExactFixture<T extends Number & Comparable<T>, E extends Enum<E>, K> {

    List<? extends Number> extendsNumber;
    Consumer<? super Integer> superInteger;
    List<?> unbounded;
    Map<String, List<? extends CharSequence>> nestedWildcard;
    Map<String, List<Integer>> nested;
    T variable;
    E selfBounded;
    K plain;
    T[] variables;
    List<T>[] listsOfT;
    List<String>[][] listMatrix;
    Map<K, List<E>>[] mapsOfVariables;
    List rawList;
    List[] rawLists;
    String[] strings;
    int primitive;
}
