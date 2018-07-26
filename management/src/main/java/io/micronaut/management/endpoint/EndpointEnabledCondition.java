/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.management.endpoint.annotation.Endpoint;

import java.util.Optional;

/**
 * A condition that checks whether an {@link Endpoint} is enabled.
 *
 * @author James Kleeh
 */
public class EndpointEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        AnnotationMetadataProvider component = context.getComponent();
        AnnotationMetadata annotationMetadata = component.getAnnotationMetadata();

        if (annotationMetadata.hasDeclaredAnnotation(Endpoint.class)) {

            Boolean defaultEnabled = annotationMetadata.getValue(Endpoint.class, "defaultEnabled", Boolean.class).orElse(true);
            String prefix = annotationMetadata.getValue(Endpoint.class, "prefix", String.class).orElse(null);
            String id = annotationMetadata.getValue(Endpoint.class, "value", String.class).orElse(null);
            String defaultId = annotationMetadata.getValue(Endpoint.class, "defaultConfigurationId", String.class).orElse(null);

            BeanContext beanContext = context.getBeanContext();
            if (beanContext instanceof PropertyResolver) {
                PropertyResolver propertyResolver = (PropertyResolver) beanContext;
                Optional<Boolean> enabled = propertyResolver.getProperty(String.format("%s.%s.enabled", prefix, id), Boolean.class);
                if (enabled.isPresent()) {
                    return enabled.get();
                } else {
                    enabled = propertyResolver.getProperty(String.format("%s.%s.enabled", prefix, defaultId), Boolean.class);
                    return enabled.orElse(defaultEnabled);
                }
            }
        }

        return true;
    }
}
