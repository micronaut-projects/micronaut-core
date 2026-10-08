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
import io.micronaut.core.type.WildcardArgument

/**
 * Renders an argument the way a declaration writes it, so that an assertion reads like the source it pins: a type
 * with its type arguments, {@code !raw} after a raw type, a type variable left unresolved as
 * {@code T extends Bound}, a type resolved in place of a variable as {@code Integer=X}, and a wildcard with its
 * bounds. An array of a variable is {@code (T extends Bound)[]}.
 *
 * <p>Shared by the specs of every processor, which compile the same sources and must agree about the answers.</p>
 */
class TypeArguments {

    /**
     * Renders the argument.
     *
     * @param argument The argument
     * @return The rendered argument
     */
    static String render(Argument<?> argument) {
        renderAt(argument, 0)
    }

    private static String renderAt(Argument<?> argument, int depth) {
        if (depth > 4) {
            return '...'
        }
        if (argument instanceof WildcardArgument) {
            WildcardArgument<?> wildcard = (WildcardArgument<?>) argument
            if (wildcard.hasLowerBound()) {
                return '? super ' + wildcard.lowerBounds.collect { renderAt(it, depth + 1) }.join(' & ')
            }
            if (wildcard.upperBounds*.type != [Object]) {
                return '? extends ' + wildcard.upperBounds.collect { renderAt(it, depth + 1) }.join(' & ')
            }
            return '?'
        }
        if (argument instanceof GenericPlaceholder && !((GenericPlaceholder<?>) argument).resolved) {
            GenericPlaceholder<?> variable = (GenericPlaceholder<?>) argument
            String rendered = variable.variableName + ' extends ' + variable.bounds.collect { renderAt(it, depth + 1) }.join(' & ')
            int dimensions = 0
            for (Class<?> type = argument.type; type.isArray(); type = type.componentType) {
                dimensions++
            }
            return dimensions == 0 ? rendered : '(' + rendered + ')' + ('[]' * dimensions)
        }
        StringBuilder rendered = new StringBuilder(argument.type.simpleName)
        if (argument instanceof GenericPlaceholder) {
            rendered.append('=').append(((GenericPlaceholder<?>) argument).variableName)
        }
        if (argument.isRawType()) {
            rendered.append('!raw')
        }
        if (argument.typeParameters.length > 0) {
            rendered.append('<').append(argument.typeParameters.collect { renderAt(it, depth + 1) }.join(', ')).append('>')
        }
        return rendered.toString()
    }
}
