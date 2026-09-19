/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.context.python.runtime.model;

import java.util.List;

/**
 * A typed, named and annotated argument: a constructor or method parameter, a return type, a property type or a type
 * argument. Types are recorded as binary class names ({@code int}, {@code java.lang.String}, {@code [I}).
 *
 * @param name               The argument name
 * @param typeName           The binary name of the erased type
 * @param annotationMetadata The annotation metadata
 * @param typeArguments      The type arguments, named after the type variables they bind
 * @since 5.3.0
 */
public record ArgumentModel(String name, String typeName, AnnotationMetadataModel annotationMetadata, List<ArgumentModel> typeArguments) {
}
