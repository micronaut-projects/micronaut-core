/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.particleframework.context.el;

import java.util.Map;

/**
 * An interface for resolving expression values
 *
 * @author graemerocher
 * @since 1.0
 */
public interface ExpressionEvaluator {

    /**
     * Evaluate an expression for the given arguments
     *
     * @param bindingBean The type of an existing bean that will be used as the script binding
     * @param expression The expression
     * @param expectedType The expected resulting type
     * @param <T>
     * @return The value
     */
    <T> T evaluate(Class<?> bindingBean, String expression, Class<T> expectedType);

    /**
     * Evaluate an expression for the given arguments
     *
     * @param variables The variables
     * @param expression The expression
     * @param expectedType The expected resulting type
     * @param <T>
     * @return The value
     */
    <T> T evaluate(Map<String, Object> variables, String expression, Class<T> expectedType);
}
