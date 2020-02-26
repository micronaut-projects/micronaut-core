/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.ast;

/**
 * A member element is an element that is contained within a {@link ClassElement}.
 * The {@link #getDeclaringType()} method returns the class that declares the element.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface MemberElement extends Element {

    /**
     * @return The declaring type of the element.
     */
    ClassElement getDeclaringType();

    /**
     * The owing type is the type that owns this element. This can differ from {@link #getDeclaringType()}
     * in the case of inheritance since this method will return the subclass that owners the inherited member,
     * whilst {@link #getDeclaringType()} will return the super class that declares the type.
     *
     * @return The owning type.
     */
    default ClassElement getOwningType() {
        return getDeclaringType();
    }
}
