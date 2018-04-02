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
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.http.client.Client;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/**
 * Adds Micronaut Client beans to a Spring application context.
 */
public class HttpClientBeanProcessor implements BeanFactoryPostProcessor, DisposableBean {

    protected DefaultBeanContext defaultApplicationContext;

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        defaultApplicationContext = new DefaultApplicationContext();
        defaultApplicationContext.start();

        Qualifier<Object> clientQualifier = Qualifiers.byStereotype(Client.class);
        defaultApplicationContext.getBeanDefinitions(clientQualifier)
                .stream()
                .filter(bd -> bd.isSingleton())
                .forEach(bd -> {
                    BeanDefinitionBuilder beanBuildr = BeanDefinitionBuilder
                            .rootBeanDefinition(MicronautSpringBeanFactory.class.getName());
                    beanBuildr.addPropertyValue("micronautBeanType", bd.getBeanType());
                    beanBuildr.addPropertyValue("micronautContext", defaultApplicationContext);
                    ((DefaultListableBeanFactory) beanFactory).registerBeanDefinition(bd.getName(), beanBuildr.getBeanDefinition());
                });
    }

    @Override
    public void destroy() throws Exception {
        if (defaultApplicationContext != null) {
            defaultApplicationContext.close();
        }
    }
}

