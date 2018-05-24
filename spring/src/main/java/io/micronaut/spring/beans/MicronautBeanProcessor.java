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

package io.micronaut.spring.beans;

import io.micronaut.context.DefaultApplicationContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.annotation.Annotation;

/**
 * Adds Micronaut beans to a Spring application context.  This processor will
 * find all of the singleton Micronaut beans marked with a specified stereotype
 * annotation and add them as singleton beans to the Spring application context.
 */
public class MicronautBeanProcessor implements BeanFactoryPostProcessor, DisposableBean {

    public static final String MICRONAUT_BEAN_TYPE_PROPERTY_NAME = "micronautBeanType";
    public static final String MICRONAUT_CONTEXT_PROPERTY_NAME = "micronautContext";

    protected DefaultBeanContext micronautContext;
    final protected Class<? extends Annotation> micronautBeanStereotype;

    /**
     *
     * @param stereotype The stereotype annotation associated with the
     *                   Micronaut beans which should be added to the
     *                   Spring application context.
     */
    public MicronautBeanProcessor(Class stereotype) {
        this.micronautBeanStereotype = stereotype;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        micronautContext = new DefaultApplicationContext();
        micronautContext.start();

        Qualifier<Object> micronautBeanQualifier = Qualifiers.byStereotype(micronautBeanStereotype);
        micronautContext.getBeanDefinitions(micronautBeanQualifier)
                .stream()
                .filter(beanDefinition -> beanDefinition.isSingleton())
                .forEach(definition -> {
                    final BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder
                            .rootBeanDefinition(MicronautSpringBeanFactory.class.getName());
                    beanDefinitionBuilder.addPropertyValue(MICRONAUT_BEAN_TYPE_PROPERTY_NAME, definition.getBeanType());
                    beanDefinitionBuilder.addPropertyValue(MICRONAUT_CONTEXT_PROPERTY_NAME, micronautContext);
                    ((DefaultListableBeanFactory) beanFactory).registerBeanDefinition(definition.getName(), beanDefinitionBuilder.getBeanDefinition());
                });
    }

    @Override
    public void destroy() throws Exception {
        if (micronautContext != null) {
            micronautContext.close();
        }
    }
}

