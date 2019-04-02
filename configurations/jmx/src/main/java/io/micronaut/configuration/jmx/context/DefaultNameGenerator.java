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
package io.micronaut.configuration.jmx.context;

import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ProxyBeanDefinition;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.Hashtable;

/**
 * Generates object names where the package is the domain
 * and the properties has a single key of "type" that is the
 * simple name of the class.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class DefaultNameGenerator implements NameGenerator {

    @Override
    public ObjectName generate(BeanDefinition<?> beanDefinition) throws MalformedObjectNameException {
        final Class type;
        if (beanDefinition instanceof ProxyBeanDefinition) {
            type = ((ProxyBeanDefinition<?>) beanDefinition).getTargetType();
        } else {
            type = beanDefinition.getBeanType();
        }

        Hashtable<String, String> properties = new Hashtable<>(1);
        properties.put("type", type.getSimpleName());

        return new ObjectName(getDomain(beanDefinition), properties);
    }

    /**
     * @param beanDefinition The bean definition
     * @return The domain used for the {@link ObjectName}
     */
    protected String getDomain(BeanDefinition<?> beanDefinition) {
        return beanDefinition.getBeanType().getPackage().getName();
    }
}
