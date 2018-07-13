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

package io.micronaut.context;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.Failure;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanContextConditional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract implementation of the {@link BeanContextConditional} interface.
 *
 * @author graemerocher
 * @since 1.0
 */
abstract class AbstractBeanContextConditional implements BeanContextConditional, AnnotationMetadataProvider {

    static final Logger LOG = LoggerFactory.getLogger(Condition.class);

    private final Map<Integer, Boolean> enabled = new ConcurrentHashMap<>(2);

    @Override
    public boolean isEnabled(BeanContext context) {
        int contextId = System.identityHashCode(context);
        Boolean enabled = this.enabled.get(contextId);
        if (enabled == null) {
            AnnotationMetadata annotationMetadata = getAnnotationMetadata();
            Condition condition = annotationMetadata.hasStereotype(Requires.class) ? new RequiresCondition(annotationMetadata) : null;
            DefaultConditionContext<AbstractBeanContextConditional> conditionContext = new DefaultConditionContext<>(context, this);
            enabled = condition == null || condition.matches(conditionContext);
            if (LOG.isDebugEnabled() && !enabled) {
                if (this instanceof BeanConfiguration) {
                    LOG.debug(this + " will not be loaded due to failing conditions:");
                } else {
                    LOG.debug("Bean [" + this + "] will not be loaded due to failing conditions:");
                }
                for (Failure failure : conditionContext.getFailures()) {
                    LOG.debug("* {}", failure.getMessage());
                }
            }
            this.enabled.put(contextId, enabled);
        }
        return enabled;
    }
}
