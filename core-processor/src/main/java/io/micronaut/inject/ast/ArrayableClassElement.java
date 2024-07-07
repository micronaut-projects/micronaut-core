/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

/**
 * Interface for class elements that can be converted to/from an array type.
 */
@Internal
public interface ArrayableClassElement extends ClassElement {
    @Override
    default @NonNull ClassElement toArray() {
        return withArrayDimensions(getArrayDimensions() + 1);
    }

    @Override
    default @NonNull ClassElement fromArray() {
        if (!isArray()) {
            throw new IllegalStateException("Not an array");
        }
        return withArrayDimensions(getArrayDimensions() - 1);
    }

    /**
     * Convert the class element to an element for the same type, but with the given number of array dimensions.
     * Do not mutate the existing instance. Create a new instance instead.
     *
     * @param arrayDimensions The number of array dimensions of the new class element
     * @return A new class element
     */
    ClassElement withArrayDimensions(int arrayDimensions);
}
