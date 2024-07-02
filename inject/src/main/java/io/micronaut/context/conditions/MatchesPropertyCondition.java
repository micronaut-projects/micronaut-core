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

import io.micronaut.context.BeanContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyResolver;

/**
 * Matches property condition.
 *
 * @param property     The property
 * @param value        The value
 * @param defaultValue The default value
 * @param condition    The condition
 * @author Denis Stepanov
 * @since 4.6
 */
@UsedByGeneratedCode
@Internal
public record MatchesPropertyCondition(@NonNull String property,
                                       @Nullable String value,
                                       @Nullable String defaultValue,
                                       @NonNull Condition condition) implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        BeanContext beanContext = context.getBeanContext();
        if (beanContext instanceof PropertyResolver propertyResolver) {
            boolean contains = propertyResolver.containsProperties(property);
            if (!contains && StringUtils.isEmpty(defaultValue)) {
                switch (condition) {
                    case CONTAINS, PATTERN ->
                        context.fail("Required property [" + property + "] not present");
                    case EQUALS ->
                        context.fail("Required property [" + property + "] with value [" + value + "] not present");
                    case NOT_EQUALS -> {
                        return true;
                    }
                }
                return false;
            } else if (condition == Condition.CONTAINS) {
                return true;
            }
            String resolved = resolvePropertyValue(property, propertyResolver, defaultValue);
            return switch (condition) {
                case EQUALS -> {
                    boolean result = resolved != null && resolved.equals(value);
                    if (!result) {
                        context.fail("Property [" + property + "] with value [" + resolved + "] does not equal required value: " + value);
                    }
                    yield result;
                }
                case NOT_EQUALS -> {
                    boolean result = resolved == null || !resolved.equals(value);
                    if (!result) {
                        context.fail("Property [" + property + "] with value [" + resolved + "] should not equal: " + value);
                    }
                    yield result;
                }
                case PATTERN -> {
                    boolean result = resolved != null && resolved.matches(value);
                    if (!result) {
                        context.fail("Property [" + property + "] with value [" + resolved + "] does not match required pattern: " + value);
                    }
                    yield result;
                }
                default -> throw new IllegalStateException("Unexpected value: " + condition);
            };
        }
        context.fail("Bean requires property but BeanContext does not support property resolution");
        return false;
    }

    private String resolvePropertyValue(String property, PropertyResolver propertyResolver, String defaultValue) {
        return propertyResolver.getProperty(property, ConversionContext.STRING).orElse(defaultValue);
    }

    public enum Condition {
        CONTAINS, EQUALS, NOT_EQUALS, PATTERN
    }

}
