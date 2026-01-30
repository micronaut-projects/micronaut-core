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
package io.micronaut.inject;

import io.micronaut.inject.writer.OriginatingElements;
import io.micronaut.sourcegen.model.ObjectDef;
import org.jspecify.annotations.Nullable;

/**
 * Aggregates the generated {@link ObjectDef} together with service metadata.
 *
 * @param objectDef           The generated object definition
 * @param serviceClass        The service to be registered
 * @param originatingElements The originating elements
 * @author Denis Stepanov
 * @since 5.0
 */
public record OutputObjectDef(ObjectDef objectDef, @Nullable Class<?> serviceClass, OriginatingElements originatingElements) {
}
