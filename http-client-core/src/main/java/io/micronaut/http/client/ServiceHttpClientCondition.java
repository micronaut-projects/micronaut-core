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
package io.micronaut.http.client;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.Named;
import io.micronaut.core.value.ValueResolver;

import java.util.Optional;

/**
 * Disables the client beans if the appropriate configuration is not present.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
final class ServiceHttpClientCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        AnnotationMetadataProvider component = context.getComponent();
        BeanContext beanContext = context.getBeanContext();

        if (beanContext instanceof ApplicationContext) {
            Environment env = ((ApplicationContext) beanContext).getEnvironment();
            if (component instanceof ValueResolver) {
                Optional<String> optional = ((ValueResolver) component).get(Named.class.getName(), String.class);
                if (optional.isPresent()) {
                    String serviceName = optional.get();
                    String urlProp = ServiceHttpClientConfiguration.PREFIX + "." + serviceName + ".url";
                    return env.containsProperty(urlProp) || env.containsProperty(urlProp + "s");
                }
            }
        }
        return true;
    }
}
