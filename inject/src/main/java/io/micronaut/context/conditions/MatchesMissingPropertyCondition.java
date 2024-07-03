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
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.value.PropertyResolver;

import java.util.Objects;

/**
 * Missing property condition.
 *
 * @param property The property
 * @author Denis Stepanov
 * @since 4.6
 */
@UsedByGeneratedCode
@Internal
public record MatchesMissingPropertyCondition(String property) implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        BeanContext beanContext = context.getBeanContext();
        if (beanContext instanceof PropertyResolver propertyResolver) {
            if (propertyResolver.containsProperties(property)) {
                context.fail("Property [" + property + "] present");
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MatchesMissingPropertyCondition that = (MatchesMissingPropertyCondition) o;
        return Objects.equals(property, that.property);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(property);
    }

    @Override
    public String toString() {
        return "MatchesMissingPropertyCondition{" +
            "property='" + property + '\'' +
            '}';
    }
}
