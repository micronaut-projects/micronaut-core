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

/**
 * Stores data about an element that references a field.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface FieldElement extends TypedElement, MemberElement {
    /**
     * Obtain the generic type with the associated annotation metadata for the field.
     * @return The generic field
     */
    default ClassElement getGenericField() {
        return getGenericType();
    }
}
