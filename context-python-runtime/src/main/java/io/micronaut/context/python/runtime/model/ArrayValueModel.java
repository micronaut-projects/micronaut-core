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
 * An array-valued annotation member with a recorded component kind, so that the backend materializes the same array
 * type the build-time writer would have emitted.
 *
 * @param componentKind The component kind
 * @param primitive     Whether the component type is primitive
 * @param elements      The elements
 * @since 5.3.0
 */
public record ArrayValueModel(ValueKind componentKind, boolean primitive, List<Object> elements) {
}
