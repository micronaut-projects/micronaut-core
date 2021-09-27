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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;

import java.util.List;

/**
 * Represents a wildcard. For compatibility, this wildcard acts like its first upper bound when used as a
 * {@link ClassElement}.
 */
@Experimental
public interface WildcardElement extends ClassElement {
    /**
     * @return The upper bounds of this wildcard. Never empty. To match this wildcard, a type must be assignable to all
     * upper bounds (must extend all upper bounds).
     */
    @NonNull
    List<? extends ClassElement> getUpperBounds();

    /**
     * @return The lower bounds of this wildcard. May be empty. To match this wildcard, a type must be assignable from
     * all lower bounds (must be a supertype of all lower bounds).
     */
    @NonNull
    List<? extends ClassElement> getLowerBounds();
}
