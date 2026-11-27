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
package io.micronaut.python.processing.model;

import io.micronaut.core.annotation.Experimental;

import java.util.Objects;

/**
 * The default of a dataclass field that is not a literal, such as an enum member
 * ({@code status: Status = Status.NEW}), a call or a name. The processor does not run user code, so only
 * Python can evaluate it: it is neither a value the generated code can reproduce nor {@code None}.
 *
 * @param expression The source of the default expression
 * @since 5.2.27
 */
@Experimental
public record UnevaluatedDefaultDef(String expression) {

    public UnevaluatedDefaultDef {
        Objects.requireNonNull(expression, "Default expression cannot be null");
    }

    @Override
    public String toString() {
        return expression;
    }
}
