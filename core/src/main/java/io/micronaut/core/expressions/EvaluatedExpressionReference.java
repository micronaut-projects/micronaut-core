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
package io.micronaut.core.expressions;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wrapper for annotation value, containing evaluated expressions and
 * class name for generated expression class. This class is only used
 * at compilation time as part of compile-time annotation metadata.
 *
 * @param annotationValue initial annotation value which is treated as evaluated expression
 * @param annotationName name of the annotation in which evaluated expression is used.
 * @param annotationMember annotation member for which evaluated expression is used
 * @param expressionClassName name for the class which is generated at compilation time and contains expression evaluation logic
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public record EvaluatedExpressionReference(@NonNull Object annotationValue,
                                           @NonNull String annotationName,
                                           @NonNull String annotationMember,
                                           @NonNull String expressionClassName) {

    public static final String EXPR_SUFFIX = "$Expr";

    private static final Map<String, Integer> CLASS_NAME_INDEXES = new ConcurrentHashMap<>();

    /**
     * Provides next expression index for passed class name. In general indexes are needed only
     * to make names of generated expression classes unique and avoid conflicts in cases when
     * multiple expressions are defined in the same class. On each invocation with the same
     * argument this method will return value incremented by 1. On first invocation it will return 0
     *
     * @param className name of class owning evaluated expression
     * @return next index
     */
    public static Integer nextIndex(String className) {
        if (CLASS_NAME_INDEXES.containsKey(className)) {
            return CLASS_NAME_INDEXES.merge(className, 1, Integer::sum);
        }

        CLASS_NAME_INDEXES.put(className, 0);
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EvaluatedExpressionReference that = (EvaluatedExpressionReference) o;
        return expressionClassName.equals(that.expressionClassName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expressionClassName);
    }
}
