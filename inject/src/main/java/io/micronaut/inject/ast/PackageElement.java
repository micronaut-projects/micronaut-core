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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;

import java.util.Objects;

/**
 * Models a package in source code.
 *
 * @author graemerocher
 * @since 3.0.0
 */
public interface PackageElement extends Element {
    /**
     * The default package.
     */
    PackageElement DEFAULT_PACKAGE = PackageElement.of("");

    /**
     * Creates a new package element for the given name.
     * @param name The package name
     * @return The package element
     */
    static @NonNull PackageElement of(@NonNull String name) {
        Objects.requireNonNull(name, "Name cannot be null");
        return new SimplePackageElement(name);
    }

    /**
     * Is unnamed package?
     *
     * @return true if unnamed
     * @since 3.7.0
     */
    default boolean isUnnamed() {
        return StringUtils.isEmpty(getName());
    }
}
