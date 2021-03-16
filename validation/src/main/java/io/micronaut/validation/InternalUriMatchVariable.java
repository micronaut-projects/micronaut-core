/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.validation;

import io.micronaut.core.annotation.Internal;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Represents a variable in a URI template.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Deprecated
@Internal
public final class InternalUriMatchVariable {

    private static final List<Character> OPTIONAL_OPERATORS = Arrays.asList('/', '#', '?', '&');

    private final String name;
    private final char modifier;
    private final char operator;

    /**
     *
     * @param name The variable name
     * @param modifier The modifier
     * @param operator The operator
     */
    InternalUriMatchVariable(String name, char modifier, char operator) {
        this.name = name;
        this.modifier = modifier;
        this.operator = operator;
    }

    /**
     * @return The variable name
     */
    public String getName() {
        return name;
    }

    /**
     * @return True if the variable is exploded
     */
    public boolean isExploded() {
        return modifier == '*';
    }

    /**
     * @return true if the variable part of a query.
     */
    public boolean isQuery() {
        return operator == '?' || operator == '#' || operator == '&';
    }

    /**
     * An optional variable is one that will allow the route to match
     * if it is not present.
     *
     * @return True if the variable is optional
     */
    public boolean isOptional() {
        return OPTIONAL_OPERATORS.contains(operator);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InternalUriMatchVariable)) {
            return false;
        }
        InternalUriMatchVariable that = (InternalUriMatchVariable) o;

        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
