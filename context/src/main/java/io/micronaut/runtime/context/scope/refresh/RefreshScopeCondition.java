/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.runtime.context.scope.refresh;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.env.Environment;

import java.util.Arrays;
import java.util.Set;

/**
 * As an optimization, RefreshScope is disabled for function and android environments when not under test.
 *
 * @author Tim Yates
 * @since 4.2.2
 */
public class RefreshScopeCondition implements Condition {

    private static final String[] DISABLED_ENVIRONMENTS = new String[]{Environment.FUNCTION, Environment.ANDROID};

    @Override
    public boolean matches(ConditionContext context) {
        BeanContext beanContext = context.getBeanContext();

        if (beanContext instanceof ApplicationContext applicationContext) {
            Environment environment = applicationContext.getEnvironment();
            Set<String> activeNames = environment.getActiveNames();

            boolean disabledEnvironment = Arrays.stream(DISABLED_ENVIRONMENTS).anyMatch(activeNames::contains);
            boolean isUnderTest = activeNames.contains(Environment.TEST);

            if (disabledEnvironment && !isUnderTest) {
                context.fail("Refresh scope is disabled for " + Environment.FUNCTION + " and " + Environment.ANDROID + " environments when not under test.");
                return false;
            }
        }
        return true;
    }
}
