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
 * A method the definition invokes: an injection point, a setter or a lifecycle callback.
 *
 * @param method             The method
 * @param annotationMetadata The metadata the definition records for the method
 * @param injectionPoints    The resolution strategy of each parameter
 * @param optional           Whether the injection is skipped when its property is absent
 * @param setter             Whether the method is a property setter
 * @param postConstruct      Whether the method is a post-construct callback
 * @param preDestroy         Whether the method is a pre-destroy callback
 * @param required           Whether the injection is required, so the method is invoked without checking that every argument resolved
 * @param guard              The property the injection is guarded by when it is optional, or null
 * @since 5.3.0
 */
public record InjectedMethodModel(MethodModel method, AnnotationMetadataModel annotationMetadata,
                                  List<InjectionPointModel> injectionPoints, boolean optional, boolean setter,
                                  boolean postConstruct, boolean preDestroy, boolean required,
                                  @Nullable PropertyGuardModel guard) {
}
