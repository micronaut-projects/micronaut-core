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
package io.micronaut.inject.test

import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder

/**
 * What an argument says about the type variable it stands for, in the shapes an assertion wants: the names of
 * its bounds, and the name the variable was declared with. Both answer {@code null} for an argument that is
 * not a variable, so a table of expectations can hold the concrete cases beside the variable ones.
 *
 * <p>Shared by the specs of every processor, which compile the same sources and must agree about the answers.</p>
 */
class Placeholders {

    /**
     * The names of the bounds the variable declares.
     *
     * @param argument The argument
     * @return The names, or {@code null} where the argument is not a variable
     */
    static List<String> bounds(Argument<?> argument) {
        argument instanceof GenericPlaceholder ? ((GenericPlaceholder<?>) argument).bounds*.type*.name : null
    }

    /**
     * The name the variable was declared with, which is not the name of the argument: an argument that is a
     * type argument is named after the parameter it stands in for.
     *
     * @param argument The argument
     * @return The name, or {@code null} where the argument is not a variable
     */
    static String variableName(Argument<?> argument) {
        argument instanceof GenericPlaceholder ? ((GenericPlaceholder<?>) argument).variableName : null
    }
}
