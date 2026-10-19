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
package io.micronaut.context.python;

import io.micronaut.core.annotation.Internal;

import java.util.List;

/**
 * The dataclass wrappers and Python class references determined by the Python compiler.
 * Implementations are singleton beans and services, so application and bootstrap contexts can
 * register their Python views without inspecting wrappers or creating target type mappings.
 *
 * @since 5.3.0
 */
@Internal
public interface PythonDataclassProvider {

    /**
     * @return The precomputed dataclass metadata
     */
    List<Entry> entries();

    /**
     * A generated dataclass wrapper and the Python class to resolve lazily in each calling context.
     *
     * @param type The generated Java class
     * @param pythonClass The Python class reference
     */
    record Entry(Class<?> type, PythonContextRuntime.PythonClassReference pythonClass) {
    }
}
