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

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;

import io.micronaut.core.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link BeanResolutionContext}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public final class DefaultBeanResolutionContext extends AbstractBeanResolutionContext {
    private final Map<BeanIdentifier, Object> beansInCreation = new ConcurrentHashMap<>(5);

    /**
     * @param context        The bean context
     * @param rootDefinition The bean root definition
     */
    public DefaultBeanResolutionContext(BeanContext context, BeanDefinition rootDefinition) {
        super(context, rootDefinition);
    }

    /**
     * @param context        The bean context
     * @param rootDefinition The bean root definition
     * @param beansInCreation the beans in creation
     */
    public DefaultBeanResolutionContext(BeanContext context, BeanDefinition rootDefinition, Map<BeanIdentifier, Object> beansInCreation) {
        super(context, rootDefinition);
        if (beansInCreation != null) {
            this.beansInCreation.putAll(beansInCreation);
        }
    }

    @Override
    public BeanResolutionContext copy() {
        DefaultBeanResolutionContext copy = new DefaultBeanResolutionContext(context, rootDefinition);
        copy.copyStateFrom(this);
        return copy;
    }

    @Override
    public void close() {
        beansInCreation.clear();
    }

    @Override
    public <T> void addInFlightBean(BeanIdentifier beanIdentifier, T instance) {
        beansInCreation.put(beanIdentifier, instance);
    }

    @Override
    public void removeInFlightBean(BeanIdentifier beanIdentifier) {
        beansInCreation.remove(beanIdentifier);
    }

    @Nullable
    @Override
    public <T> T getInFlightBean(BeanIdentifier beanIdentifier) {
        //noinspection unchecked
        return (T) beansInCreation.get(beanIdentifier);
    }
}
