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
package io.micronaut.python.annotation.processing.test.classargs;

import io.micronaut.core.type.Argument;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Java API taking classes at run time, in the shapes module examples use: a document class next
 * to a lambda or a query (an OpenSearch style client), a class array and mixed class/argument
 * parameters.
 */
public final class ClassArgumentApi {

    private ClassArgumentApi() {
    }

    public static <T> String search(Function<String, String> query, Class<T> documentClass) {
        return "function:" + query.apply("q") + ":" + documentClass.getName();
    }

    public static <T> String search(String query, Class<T> documentClass) {
        return "query:" + query + ":" + documentClass.getName();
    }

    public static String describe(Class<?>... types) {
        return Arrays.stream(types).map(Class::getName).collect(Collectors.joining(","));
    }

    public static String describe(Class<?> type, Argument<?>... typeArguments) {
        return type.getName() + "<" + Arrays.stream(typeArguments).map(argument -> argument.getTypeString(true)).collect(Collectors.joining(",")) + ">";
    }

    public static String describe(Argument<?> argument) {
        return "argument:" + argument.getTypeString(true);
    }

    public static String describe(Class<?> type) {
        return "class:" + type.getName();
    }
}
