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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.Experimental;

/**
 * Used in conjunction with {@link ClassElement#hasUnresolvedTypes(UnresolvedTypeKind...)} to support
 * checking whether a type has unresolved type references.
 *
 * <p>Currently only checking the interface and class hierarchy is supported but this enum could be extended further
 * in the future to support other cases.
 * </p>
 *
 * @since 4.7.18
 * @see ClassElement#hasUnresolvedTypes(UnresolvedTypeKind...)
 */
@Experimental
public enum UnresolvedTypeKind {
    /**
     * An interface type reference.
     */
    INTERFACE,
    /**
     * A super class type reference.
     */
    SUPERCLASS
}
