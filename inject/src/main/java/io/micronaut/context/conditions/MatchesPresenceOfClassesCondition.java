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

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;

/**
 * Matches presence of classes condition.
 *
 * @param classes The classes
 * @author Denis Stepanov
 * @since 4.6
 */
@UsedByGeneratedCode
@Internal
public record MatchesPresenceOfClassesCondition(AnnotationClassValue<?>[] classes) implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        for (AnnotationClassValue<?> classValue : classes) {
            if (classValue.getType().isEmpty()) {
                context.fail("Class [" + classValue.getName() + "] is not present");
                return false;
            }
        }
        return true;
    }
}
