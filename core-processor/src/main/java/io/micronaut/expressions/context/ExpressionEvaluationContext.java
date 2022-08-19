/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.expressions.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.TypedElement;

import java.util.List;

/**
 * The context against which expressions are evaluated.
 * Context methods, properties and method parameters can be referenced
 * in evaluated expressions.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public sealed interface ExpressionEvaluationContext permits BeanContextExpressionEvaluationContext,
                                                            MethodExpressionEvaluationContext,
                                                            CompositeExpressionEvaluationContext {
    /**
     * Returns list of methods registered in expression evaluation context
     * and matching provided name.
     *
     * @param name method name to look for
     * @return list of matching methods
     */
    @NonNull
    List<MethodElement> getMethods(@NonNull String name);

    /**
     * Provides list of typed elements registered in expression evaluation context
     * and matching provided name.
     *
     * @param name element name to look for
     * @return list of matching elements
     */
    @NonNull
    List<? extends TypedElement> getTypedElements(@NonNull String name);
}
