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
package org.grails.plugins.micronaut;

import io.micronaut.context.DefaultApplicationContext;
import org.springframework.beans.factory.FactoryBean;

import java.util.Collection;
import java.util.Optional;

/**
 * A spring FactoryBean for adding Micronaut beans to a
 * Spring application context.
 */
class MicronautSpringBeanFactory implements FactoryBean {

    private Class micronautBeanType;
    private DefaultApplicationContext micronautContext;

    public void setMicronautBeanType(Class micronautBeanType) {
        this.micronautBeanType = micronautBeanType;
    }

    public void setMicronautContext(DefaultApplicationContext micronautContext) {
        this.micronautContext = micronautContext;
    }

    @Override
    public Object getObject() throws Exception {
        Collection<?> beans = micronautContext.getBeansOfType(micronautBeanType);
        if (beans.size() == 1) {
            Optional<?> beanOptional = beans.stream().findFirst();
            if (beanOptional.isPresent()) {
                return beanOptional.get();
            }
        }

        // TODO
        throw new IllegalStateException("Something Went Wrong With Bean [" + micronautBeanType + "]");
    }

    @Override
    public Class<?> getObjectType() {
        return micronautBeanType;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
