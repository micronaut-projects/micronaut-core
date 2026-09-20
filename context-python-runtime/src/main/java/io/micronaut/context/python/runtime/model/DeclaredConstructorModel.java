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

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One constructor an introspection describes and can instantiate the bean with. The instantiating one comes first,
 * as the writer orders them.
 *
 * @param annotationMetadata The annotation metadata of the constructor
 * @param arguments          The arguments, with their own annotation metadata
 * @param creator            The static method creating the bean, or null when a constructor of the bean type does
 * @since 5.3.0
 */
public record DeclaredConstructorModel(AnnotationMetadataModel annotationMetadata, List<ArgumentModel> arguments,
                                       @Nullable MethodModel creator) {
}
