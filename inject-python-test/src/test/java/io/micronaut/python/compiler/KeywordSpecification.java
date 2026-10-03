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
package io.micronaut.python.compiler;

/**
 * A composable predicate whose combinators are Python keywords, in the style of a criteria
 * {@code Specification}: instances come back from Java and every combinator returns another one.
 */
public interface KeywordSpecification {

    static KeywordSpecification named(String name) {
        return () -> name;
    }

    String name();

    default KeywordSpecification and(KeywordSpecification other) {
        return () -> "(" + name() + " and " + other.name() + ")";
    }

    default KeywordSpecification or(KeywordSpecification other) {
        return () -> "(" + name() + " or " + other.name() + ")";
    }

    default KeywordSpecification not() {
        return () -> "not " + name();
    }

    default boolean is(KeywordSpecification other) {
        return name().equals(other.name());
    }

    default boolean in(java.util.List<String> names) {
        return names.contains(name());
    }
}
