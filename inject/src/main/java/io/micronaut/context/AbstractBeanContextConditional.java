/*
 * Copyright 2018 original authors
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
package io.micronaut.context;

import io.micronaut.context.annotation.Requirements;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.annotation.Requirements;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.inject.BeanContextConditional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract implementation of the {@link BeanContextConditional} interface
 *
 * @author graemerocher
 * @since 1.0
 */
abstract class AbstractBeanContextConditional implements BeanContextConditional, AnnotationMetadataProvider {

    private final Map<Integer,Boolean> enabled = new ConcurrentHashMap<>(2);

    @Override
    public boolean isEnabled(BeanContext context) {
        int contextId = context.hashCode();
        Boolean enabled = this.enabled.get(contextId);
        if(enabled == null) {
            AnnotationMetadata annotationMetadata = getAnnotationMetadata();
            Condition condition = annotationMetadata.hasStereotype(Requirements.class) || annotationMetadata.hasStereotype(Requires.class) ? new RequiresCondition(annotationMetadata) : null;
            enabled = condition == null || condition.matches(new DefaultConditionContext<>(context, this));
            this.enabled.put(contextId, enabled);
        }
        return enabled;
    }
}
