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
package io.micronaut.python.processing.model;

import io.micronaut.core.annotation.Experimental;

import java.util.Objects;

/**
 * The default of a dataclass field declared through {@code field(default_factory=...)}: the factory is called
 * by Python for every instance, so the default is not a value the generated code can reproduce. Only the
 * builtin {@code list}, {@code dict} and {@code set} factories are recognised as the empty collection.
 *
 * @param name The factory function name as written, for example {@code list} or {@code dataclasses.field}
 * @since 5.2.0
 */
@Experimental
public record DefaultFactoryDef(String name) {

    public DefaultFactoryDef {
        Objects.requireNonNull(name, "Default factory name cannot be null");
    }

    /**
     * @param builtin A builtin factory name ({@code list}, {@code dict}, {@code set})
     * @return Whether the factory is that builtin, possibly qualified
     */
    public boolean isBuiltin(String builtin) {
        return builtin.equals(name) || name.endsWith("." + builtin);
    }

    @Override
    public String toString() {
        return name + "()";
    }
}
