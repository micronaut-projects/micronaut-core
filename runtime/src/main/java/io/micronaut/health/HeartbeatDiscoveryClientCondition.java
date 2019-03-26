/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.health;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.discovery.CompositeDiscoveryClient;

/**
 * Custom condition to conditionally enable the heart beat.
 *
 * @author graemerocher
 * @since 1.1
 */
public final class HeartbeatDiscoveryClientCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        final ApplicationContext beanContext = (ApplicationContext) context.getBeanContext();
        final Boolean enabled = beanContext.getProperty(HeartbeatConfiguration.ENABLED, Boolean.class).orElse(null);

        final CompositeDiscoveryClient discoveryClient = beanContext.getBean(CompositeDiscoveryClient.class);
        if ((enabled != null && enabled) || ArrayUtils.isNotEmpty(discoveryClient.getDiscoveryClients())) {
            return true;
        } else {
            context.fail("Heartbeat not enabled since no Discovery client active");
            return false;
        }
    }
}
