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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.sourcegen.model.ExpressionDef;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A {@link ParameterElement} whose declared default value can be materialised by the
 * <i>caller</i>, as an expression evaluated at the invocation site.
 *
 * <p>This supports languages that compile a default argument to an accessor the caller invokes.
 * Scala, for example, compiles {@code class Greeter(greeting: String = "hello")} to a single
 * constructor plus a static {@code $lessinit$greater$default$1()} getter, so an absent argument
 * can be filled in like:</p>
 *
 * <pre>{@code new Greeter(greeting != null ? greeting : Greeter.$lessinit$greater$default$1())}</pre>
 *
 * <p>It is deliberately <i>not</i> a general mechanism for default arguments. Languages that
 * evaluate defaults inside the callee cannot implement it — a Kotlin default may reference
 * {@code this} and preceding parameters and exists only within the synthetic {@code $default}
 * body (see {@link KotlinParameterElement}), and an interpreted language's default may not be
 * expressible as bytecode at all. Such languages report {@link ParameterElement#hasDefault()}
 * without implementing this interface, and supply the value by their own means.</p>
 *
 * @author graemerocher
 * @since 5.2.0
 */
@Experimental
public interface DefaultValueProvidingParameterElement extends ParameterElement {

    /**
     * Supplies an expression evaluating to this parameter's declared default value.
     *
     * <p>The returned expression must be assignable to the erasure of {@link #getType()} and must
     * be safe to evaluate at most once per invocation, in argument position. Implementations
     * should be side effect free, as this method may be called more than once for the same
     * parameter.</p>
     *
     * @param target The receiver of the call the parameter belongs to, or {@code null} when the
     *               default is obtained without one — as for a constructor, or where the language
     *               emits a static accessor
     * @return The expression, or {@link Optional#empty()} if the default cannot be materialised at
     *         the call site, in which case the caller falls back to the default value of the
     *         parameter's type
     */
    Optional<ExpressionDef> defaultValueExpression(@Nullable ExpressionDef target);

}
