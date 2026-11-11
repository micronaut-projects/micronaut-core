/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.server.cors;

import io.micronaut.context.BeanContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.http.server.HttpServerConfiguration;

/**
 * Passes if a cross-origin response policy is configured. Without one there is no populator, and
 * so no response filter that runs for every request only to add nothing.
 *
 * @since 5.3.0
 */
@Internal
final class CrossOriginPoliciesCondition implements Condition {
    private static final String PREFIX = HttpServerConfiguration.PREFIX + ".cors.";

    @Override
    public boolean matches(ConditionContext context) {
        BeanContext beanContext = context.getBeanContext();
        if (beanContext instanceof PropertyResolver resolver
            && (resolver.containsProperty(PREFIX + "cross-origin-resource-policy")
                || resolver.containsProperty(PREFIX + "cross-origin-embedder-policy"))) {
            return true;
        }
        context.fail("No cross-origin response policy configured");
        return false;
    }
}
