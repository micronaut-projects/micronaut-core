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

import io.micronaut.core.annotation.Experimental;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Models an enum type.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface EnumElement extends ClassElement {

    /**
     * The values that make up this enum.
     *
     * @return The values
     */
    List<String> values();

    /**
     * The enum constant elements that make up this enum.
     *
     * @return The enum constant elements
     *
     * @since 3.6.0
     */
    default List<EnumConstantElement> elements() {
        return Collections.emptyList();
    }

    /**
     * Returns the static method used to resolve an enum constant by name.
     *
     * <p>Java enums use the standard {@link Enum#valueOf(Class, String)}
     * path and therefore return an empty optional. Language implementations
     * can expose their compiler-modelled lookup method so generated
     * introspections can resolve constants without reflection.</p>
     *
     * @return The enum constant lookup method, if one is available
     * @since 5.2.0
     */
    @Experimental
    default Optional<MethodElement> getEnumValueOfMethod() {
        return Optional.empty();
    }
}
