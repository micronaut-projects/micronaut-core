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
package io.micronaut.python.annotation.processing.test.generics;

import io.micronaut.context.annotation.Executable;

import java.util.List;
import java.util.Map;

/**
 * A Java contract whose signatures are written in every shape an argument has, for a Python bean to implement.
 *
 * @param <T> A variable with a recursive bound
 * @param <U> A variable with no bound
 */
public interface StructureContract<T extends Comparable<T>, U> {

    @Executable
    default void first(List raw,
               List<?> unbounded,
               List<Object> object,
               List<? extends Number> upper,
               List<? super Integer> lower,
               List<T> recursive,
               List<U> none,
               Map<String, List<? extends Number>> nested,
               Map<T, T> recursiveTwice,
               String[] array,
               List<String>[] arrayOfParameterized,
               T[] arrayOfRecursive,
               U[] arrayOfNone,
               T recursiveVariable,
               U noneVariable,
               String type) {
    }

    @Executable
    default void second(List raw,
                List<?> unbounded,
                List<Object> object,
                List<? extends Number> upper,
                List<? super Integer> lower,
                List<T> recursive,
                List<U> none,
                Map<String, List<? extends Number>> nested,
                Map<T, T> recursiveTwice,
                String[] array,
                List<String>[] arrayOfParameterized,
                T[] arrayOfRecursive,
                U[] arrayOfNone,
                T recursiveVariable,
                U noneVariable,
                String type) {
    }
}
