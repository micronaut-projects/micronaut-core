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
package io.micronaut.inject.visitor;

/**
 * Allows supplying configuration to the {@link VisitorContext}.
 *
 * @author graemerocher
 * @since 2.3.0
 */
public interface VisitorConfiguration {
    VisitorConfiguration DEFAULT = new VisitorConfiguration() {
    };

    /**
     * This configures whether to include type level annotations on generic arguments when materializing the AST nodes via
     * the {@link io.micronaut.inject.ast.Element} API.
     *
     * <p>If {@code true} is returned then methods like {@link io.micronaut.inject.ast.ClassElement#getTypeArguments()} will include annotations declared on the classes themselves within the annotation metadata for each resulting {@link io.micronaut.inject.ast.ClassElement} within the generic arguments.</p>
     *
     * <p>This can be undesirable in the use case where you need to differentiate annotations on the type arguments themselves vs annotations declared on the type, in which case you should return false.</p>
     *
     * @return True if annotations should be included
     * @see io.micronaut.inject.ast.ElementFactory
     */
    default boolean includeTypeLevelAnnotationsInGenericArguments() {
        return true;
    }

}
