/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.expression;

/**
 * Expression included in annotation metadata which can be evaluated at runtime.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
public interface EvaluatedExpression {

    /**
     * Evaluated expression prefix.
     */
    String EXPRESSION_PREFIX = "#{";

    /**
     * RegEx pattern used to determine whether string value in
     * annotation includes evaluated expression.
     */
    String EXPRESSION_PATTERN = ".*#\\{.*}.*";

    /**
     * Evaluate expression to obtain evaluation result.
     *
     * @param args Array of arguments which need to be passed to expression
     *             for evaluation. Args are used when expression itself is used
     *             on method and references method arguments
     * @return evaluation result
     */
    Object evaluate(Object... args);

    /**
     * Get original annotation value that was used to generated EvaluatedExpression class.
     *
     * @return the original expression
     */
    Object getInitialAnnotationValue();
}
