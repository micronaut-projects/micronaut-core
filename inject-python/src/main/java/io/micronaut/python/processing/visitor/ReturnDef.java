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

/**
 * A ReturnDef represents the return type annotation of a function.
 * <p>
 * This record captures the return type annotation from a Python function's
 * type hint (e.g., "-> int", "-> str", "-> List[str]").
 * </p>
 *
 * @param typeAnnotation The return type annotation, or null if no return type is specified.
 * @param decorators The decorators found on the return type annotation.
 * @author Micronaut Team
 * @since 5.0.0
 */
@Experimental
public record ReturnDef(
    TypeRef typeAnnotation,
    java.util.List<DecoratorDef> decorators
) implements ElementDef {

    /**
     * Creates a ReturnDef with no return type annotation.
     *
     * @return A new ReturnDef with null type annotation
     */
    public static ReturnDef none() {
        return new ReturnDef(null, java.util.List.of());
    }

    /**
     * Creates a ReturnDef with the specified type annotation.
     *
     * @param typeAnnotation The return type annotation
     * @return A new ReturnDef
     */
    public static ReturnDef of(TypeRef typeAnnotation) {
        return new ReturnDef(typeAnnotation, java.util.List.of());
    }

    /**
     * Creates a ReturnDef with the specified type annotation and decorators.
     *
     * @param typeAnnotation The return type annotation
     * @param decorators The decorators for the return type
     * @return A new ReturnDef
     */
    public static ReturnDef of(TypeRef typeAnnotation, java.util.List<DecoratorDef> decorators) {
        return new ReturnDef(typeAnnotation, decorators != null ? decorators : java.util.List.of());
    }

    @Override
    public String name() {
        return typeAnnotation != null ? typeAnnotation.name() : null;
    }
}
