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
import org.jetbrains.annotations.NotNull;

/**
 * Represents a generic element that can appear as a type argument.
 *
 * @since 4.0.0
 * @author Denis Stepanov
 */
@Experimental
public interface GenericElement extends ClassElement {

    /**
     * The native type that represents the generic element.
     * It is expected that the generic element representing 'T extends java.lang.Number`
     * should be equal to the class element `java.lang.Number`.
     * To find matching placeholders we can use this method to match the native generic type.
     *
     * @return The generic native type
     */
    @NotNull
    default Object getGenericNativeType() {
        return getNativeType();
    }

}
