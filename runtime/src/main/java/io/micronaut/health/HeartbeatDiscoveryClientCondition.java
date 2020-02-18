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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.discovery.CompositeDiscoveryClient;

import static java.lang.Boolean.FALSE;

/**
 * Custom condition to conditionally enable the heart beat.
 *
 * @author graemerocher
 * @since 1.1
 */
@Introspected
public final class HeartbeatDiscoveryClientCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        final ApplicationContext beanContext = (ApplicationContext) context.getBeanContext();
        final CompositeDiscoveryClient compositeDiscoveryClient = beanContext.getBean(CompositeDiscoveryClient.class);
        final boolean hasDiscovery = compositeDiscoveryClient.getDiscoveryClients().length > 0;
        if (hasDiscovery) {
            return true;
        } else {
            final Boolean enabled = beanContext.getProperty(HeartbeatConfiguration.ENABLED, ArgumentConversionContext.BOOLEAN).orElse(FALSE);
            if (!enabled) {
                context.fail("Heartbeat not enabled since no Discovery client active");
            }
            return enabled;
        }
    }
}
