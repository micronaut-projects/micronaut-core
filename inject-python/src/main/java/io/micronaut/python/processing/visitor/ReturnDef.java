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

/**
 * A ReturnDef represents the return type annotation of a function.
 * <p>
 * This record captures the return type annotation string from a Python function's
 * type hint (e.g., "-> int", "-> str", "-> List[str]").
 * </p>
 *
 * @param typeAnnotation The return type annotation string, or null if no return type is specified.
 * @author Micronaut Team
 * @since 5.0.0
 */
public record ReturnDef(
    String typeAnnotation
) {

    /**
     * Creates a ReturnDef with no return type annotation.
     *
     * @return A new ReturnDef with null type annotation
     */
    public static ReturnDef none() {
        return new ReturnDef(null);
    }

    /**
     * Creates a ReturnDef with the specified type annotation.
     *
     * @param typeAnnotation The return type annotation
     * @return A new ReturnDef
     */
    public static ReturnDef of(String typeAnnotation) {
        return new ReturnDef(typeAnnotation);
    }
}
