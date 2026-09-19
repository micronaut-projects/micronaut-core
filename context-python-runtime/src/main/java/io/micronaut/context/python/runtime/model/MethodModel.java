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
 * A member method by its recorded descriptor, so that backends emit direct invocations without scanning members.
 *
 * @param declaringType      The binary name of the class declaring the method
 * @param name               The method name
 * @param returnType         The return type
 * @param parameters         The parameters
 * @param annotationMetadata The method annotation metadata
 * @param isStatic           Whether the method is static
 * @since 5.3.0
 */
public record MethodModel(String declaringType, String name, ArgumentModel returnType, List<ArgumentModel> parameters,
                          AnnotationMetadataModel annotationMetadata, boolean isStatic) {
}
