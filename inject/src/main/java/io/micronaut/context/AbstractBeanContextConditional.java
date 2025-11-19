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
package io.micronaut.context;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.Failure;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanContextConditional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract implementation of the {@link BeanContextConditional} interface.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
abstract class AbstractBeanContextConditional implements BeanContextConditional, AnnotationMetadataProvider {

    @Override
    public boolean isEnabled(@NonNull BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
        AnnotationMetadata annotationMetadata = getAnnotationMetadata();
        Condition condition = annotationMetadata.hasStereotype(Requires.class) ? new RequiresCondition(annotationMetadata) : null;
        if (condition == null) {
            return true;
        }
        DefaultConditionContext<AbstractBeanContextConditional> conditionContext = new DefaultConditionContext<>(
            context,
            this,
            resolutionContext
        );
        boolean enabled = condition.matches(conditionContext);
        if (!enabled) {
            onFail(conditionContext, context);
        }
        return enabled;
    }

    protected final void onFail(DefaultConditionContext<AbstractBeanContextConditional> conditionContext, BeanContext context) {
        if (ConditionLog.LOG.isDebugEnabled()) {
            if (this instanceof BeanConfiguration) {
                ConditionLog.LOG.debug("{} will not be loaded due to failing conditions:", this);
            } else {
                ConditionLog.LOG.debug("Bean [{}] will not be loaded due to failing conditions:", this);
            }
            for (Failure failure : conditionContext.getFailures()) {
                ConditionLog.LOG.debug("* {}", failure.getMessage());
            }
        }
        DefaultBeanContext defaultBeanContext = (DefaultBeanContext) context;
        defaultBeanContext.trackDisabledComponent(conditionContext);
    }

    @SuppressWarnings("java:S3416")
    static final class ConditionLog {
        static final Logger LOG = LoggerFactory.getLogger(Condition.class);

        private ConditionLog() {
        }
    }
}
