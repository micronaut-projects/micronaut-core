/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing.visitor;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;

/**
 * Represents a member of a {@link ClassDef}.
 */
@Experimental
public sealed interface MemberDef permits AttributeDef, FunctionDef {
    /**
     * The declaring class.
     * @return The declaring class or {@code null} in the case of anonymous functions.
     */
    @Nullable
    ClassDef declaringClass();
}
