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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.SemanticVersion;
import io.micronaut.inject.BeanConfiguration;

import java.util.Optional;

/**
 * The configuration condition.
 *
 * @param configurationName The configuration name
 * @param minimumVersion    The minimum version
 * @author Denis Stepanov
 * @since 4.6
 */
@UsedByGeneratedCode
@Internal
public record MatchesConfigurationCondition(String configurationName,
                                            @Nullable String minimumVersion) implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        BeanContext beanContext = context.getBeanContext();
        Optional<BeanConfiguration> beanConfiguration = beanContext.findBeanConfiguration(configurationName);
        if (beanConfiguration.isEmpty()) {
            context.fail("Required configuration [" + configurationName + "] is not active");
            return false;
        }
        String version = beanConfiguration.get().getVersion();
        if (version != null && StringUtils.isNotEmpty(minimumVersion)) {
            boolean matches = SemanticVersion.isAtLeast(version, minimumVersion);
            if (!matches) {
                context.fail("Required configuration [" + configurationName + "] version requirements not met. Required: " + minimumVersion + ", Current: " + version);
            }
            return matches;
        }
        return true;
    }
}
