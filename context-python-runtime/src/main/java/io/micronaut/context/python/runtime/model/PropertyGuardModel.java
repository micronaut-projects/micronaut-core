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

/**
 * The property an optional injection is guarded by: the injection happens only when the configuration contains it.
 *
 * @param propertyPath The property path the configuration is asked for
 * @param multiValue   Whether the property holds several values (a map, a collection or nested configuration), which is asked for as a prefix
 * @param cliProperty  The command line property name checked as well, or null
 * @since 5.3.0
 */
public record PropertyGuardModel(String propertyPath, boolean multiValue, @Nullable String cliProperty) {
}
