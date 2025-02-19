/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.context.conditions;

import io.micronaut.context.RequiresCondition;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;

import java.util.Objects;

/**
 * The dynamic condition for requirements with expressions.
 *
 * @param annotationMetadata The annotationMetadata
 * @author Denis Stepanov
 * @since 4.6
 */
@UsedByGeneratedCode
@Internal
public record MatchesDynamicCondition(AnnotationMetadata annotationMetadata) implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        return new RequiresCondition(annotationMetadata).matches(context);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MatchesDynamicCondition that = (MatchesDynamicCondition) o;
        return Objects.equals(annotationMetadata, that.annotationMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(annotationMetadata);
    }

    @Override
    public String toString() {
        return "MatchesDynamicCondition{" +
            "annotationMetadata=" + annotationMetadata +
            '}';
    }
}

