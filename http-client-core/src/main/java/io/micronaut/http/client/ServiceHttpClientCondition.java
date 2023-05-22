/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.client;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.Named;
import io.micronaut.inject.QualifiedBeanType;

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

        if (beanContext instanceof ApplicationContext applicationContext &&
            component instanceof QualifiedBeanType<?> qualifiedBeanType) {
            Qualifier<?> declaredQualifier = qualifiedBeanType.getDeclaredQualifier();
            if (declaredQualifier instanceof Named named) {
                String serviceName = named.getName();
                String urlProp = ServiceHttpClientConfiguration.PREFIX + "." + serviceName + ".url";
                return applicationContext.containsProperty(urlProp) || applicationContext.containsProperty(urlProp + "s");
            }
        }
        return true;
    }
}
