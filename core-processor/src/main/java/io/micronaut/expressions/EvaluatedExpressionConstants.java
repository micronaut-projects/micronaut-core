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
package io.micronaut.expressions;

/**
 * Set of constants used for evaluated expressions processing.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
public class EvaluatedExpressionConstants {
    /**
     * Evaluated expression prefix.
     */
    public static final String EXPRESSION_PREFIX = "#{";

    /**
     * RegEx pattern used to determine whether string value in
     * annotation includes evaluated expression.
     */
    public static final String EXPRESSION_PATTERN = ".*#\\{.*}.*";
}
