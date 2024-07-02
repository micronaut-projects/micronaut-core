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
import io.micronaut.context.condition.TrueCondition;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.reflect.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * The custom condition.
 *
 * @param customConditionClass The custom condition class annotation value
 * @author Denis Stepanov
 * @since 4.6
 */
@UsedByGeneratedCode
@Internal
public record MatchesCustomCondition(AnnotationClassValue<?> customConditionClass) implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        final Object instance = customConditionClass.getInstance().orElse(null);
        if (instance instanceof Condition condition) {
            final boolean conditionResult = condition.matches(context);
            if (!conditionResult) {
                context.fail("Custom condition [" + instance.getClass() + "] failed evaluation");
            }
            return conditionResult;
        }
        final Class<?> conditionClass = customConditionClass.getType().orElse(null);
        if (conditionClass == null || conditionClass == TrueCondition.class || !Condition.class.isAssignableFrom(conditionClass)) {
            return true;
        }
        // try first via instantiated metadata
        Optional<? extends Condition> condition = InstantiationUtils.tryInstantiate((Class<? extends Condition>) conditionClass);
        if (condition.isPresent()) {
            boolean conditionResult = condition.get().matches(context);
            if (!conditionResult) {
                context.fail("Custom condition [" + conditionClass + "] failed evaluation");
            }
            return conditionResult;
        }
        // maybe a Groovy closure
        Optional<Constructor<?>> constructor = ReflectionUtils.findConstructor((Class) conditionClass, Object.class, Object.class);
        boolean conditionResult = constructor.flatMap(ctor ->
            InstantiationUtils.tryInstantiate(ctor, null, null)
        ).flatMap(obj -> {
            Optional<Method> method = ReflectionUtils.findMethod(obj.getClass(), "call", ConditionContext.class);
            if (method.isPresent()) {
                Object result = ReflectionUtils.invokeMethod(obj, method.get(), context);
                if (result instanceof Boolean boolean1) {
                    return Optional.of(boolean1);
                }
            }
            return Optional.empty();
        }).orElse(false);
        if (!conditionResult) {
            context.fail("Custom condition [" + conditionClass + "] failed evaluation");
        }
        return conditionResult;
    }
}
