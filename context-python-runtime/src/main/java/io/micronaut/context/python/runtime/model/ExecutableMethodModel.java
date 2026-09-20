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

/**
 * An executable method of a bean: invoked by the framework through the executable methods definition, never
 * intercepted (intercepted methods are not supported by the model backends yet).
 *
 * @param method             The method
 * @param returnArgument     The return argument, with its type-use annotation metadata
 * @param annotationMetadata The method's annotation metadata: the declared layer when {@code hierarchy} is set, the whole metadata otherwise
 * @param hierarchy          Whether the metadata is the declared layer of a hierarchy rooted at the bean's metadata
 * @param processOnStartup   Whether the method is processed on startup ({@code @Executable(processOnStartup = true)})
 * @param isAbstract         Whether the method is abstract
 * @since 5.3.0
 */
public record ExecutableMethodModel(MethodModel method, ArgumentModel returnArgument, AnnotationMetadataModel annotationMetadata,
                                    boolean hierarchy, boolean processOnStartup, boolean isAbstract) {
}
