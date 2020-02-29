/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.management.endpoint;

import io.micronaut.context.BeanContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.BeanConfiguration;

import java.util.Optional;

/**
 * Determines if the endpoints filter should be enabled.
 *
 * The filter should be enabled if security is not on the classpath
 * or it is explicitly disabled.
 *
 * @author James Kleeh
 * @since 2.0.0
 */
public class EndpointsFilterCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        BeanContext beanContext = context.getBeanContext();
        Optional<BeanConfiguration> beanConfiguration = beanContext.findBeanConfiguration("io.micronaut.security");
        if (beanConfiguration.isPresent()) {
            if (beanContext instanceof PropertyResolver) {
                PropertyResolver propertyResolver = (PropertyResolver) beanContext;
                Optional<Boolean> securityEnabled = propertyResolver.getProperty("micronaut.security.enabled", Boolean.class);
                //micronaut security is present and explicitly disabled
                return securityEnabled.isPresent() && !securityEnabled.get();
            }
        }

        //micronaut security is not present or bean context is not property resolver
        return true;
    }
}
