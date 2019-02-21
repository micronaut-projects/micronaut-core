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

package io.micronaut.core.beans;

import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of the {@link BeanIntrospector} interface that uses service loader to discovery introspections.
 *
 * @author graemerocher
 * @since 1.1
 */
class DefaultBeanIntrospector implements BeanIntrospector {

    private Map<Class, BeanIntrospectionReference> introspectionMap;

    @Nonnull
    @Override
    public <T> Optional<BeanIntrospection<T>> findIntrospection(@Nonnull Class<T> beanType) {
        final BeanIntrospectionReference reference = getIntrospections().get(beanType);
        try {
            return Optional.ofNullable(reference).map(BeanIntrospectionReference::load);
        } catch (Throwable e) {
            throw new IntrospectionException("Error loading bean introspection for type [" + beanType + "]: " + e.getMessage(), e);
        }
    }

    private Map<Class, BeanIntrospectionReference> getIntrospections() {
        Map<Class, BeanIntrospectionReference> introspectionMap = this.introspectionMap;
        if (introspectionMap == null) {
            synchronized (this) { // double check
                introspectionMap = this.introspectionMap;
                if (introspectionMap == null) {
                    introspectionMap = new HashMap<>(30);
                    final SoftServiceLoader<BeanIntrospectionReference> services = SoftServiceLoader.load(BeanIntrospectionReference.class);

                    for (ServiceDefinition<BeanIntrospectionReference> definition : services) {
                        if (definition.isPresent()) {
                            final BeanIntrospectionReference ref = definition.load();
                            introspectionMap.put(ref.getBeanType(), ref);
                        }
                    }

                    this.introspectionMap = introspectionMap;
                }
            }
        }
        return introspectionMap;
    }
}
