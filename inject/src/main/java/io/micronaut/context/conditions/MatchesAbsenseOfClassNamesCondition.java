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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.reflect.ClassUtils;

/**
 * Missing classes condition.
 *
 * @param classes The class names
 * @author Denis Stepanov
 * @since 4.6
 */
@UsedByGeneratedCode
@Internal
public record MatchesAbsenseOfClassNamesCondition(String[] classes) implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        final ClassLoader classLoader = context.getBeanContext().getClassLoader();
        for (String name : classes) {
            if (ClassUtils.isPresent(name, classLoader)) {
                context.fail("Class [" + name + "] is not absent");
                return false;
            }
        }
        return true;
    }
}
