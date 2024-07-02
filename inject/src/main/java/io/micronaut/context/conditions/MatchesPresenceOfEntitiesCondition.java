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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;

import java.util.Optional;

/**
 * Matches presence of entities condition.
 *
 * @param classes The classes
 * @author Denis Stepanov
 * @since 4.6
 */
@UsedByGeneratedCode
@Internal
public record MatchesPresenceOfEntitiesCondition(AnnotationClassValue<?>[] classes) implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        BeanContext beanContext = context.getBeanContext();
        if (beanContext instanceof ApplicationContext applicationContext) {
            for (AnnotationClassValue<?> classValue : classes) {
                final Optional<? extends Class<?>> entityType = classValue.getType();
                if (entityType.isEmpty()) {
                    context.fail("Annotation type [" + classValue.getName() + "] not present on classpath");
                    return false;
                } else {
                    Environment environment = applicationContext.getEnvironment();
                    Class annotationType = entityType.get();
                    if (environment.scan(annotationType).findFirst().isEmpty()) {
                        context.fail("No entities found in packages [" + String.join(", ", environment.getPackages()) + "] for annotation: " + annotationType);
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
