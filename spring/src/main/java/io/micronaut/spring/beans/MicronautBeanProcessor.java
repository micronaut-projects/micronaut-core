/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.context.env.DefaultEnvironment;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Adds Micronaut beans to a Spring application context.  This processor will
 * find all of the Micronaut beans of the specified types
 * and add them as beans to the Spring application context.
 *
 * @author jeffbrown
 * @since 1.0
 */
public class MicronautBeanProcessor implements BeanFactoryPostProcessor, DisposableBean, EnvironmentAware {

    private static final String MICRONAUT_BEAN_TYPE_PROPERTY_NAME = "micronautBeanType";
    private static final String MICRONAUT_CONTEXT_PROPERTY_NAME = "micronautContext";
    private static final String MICRONAUT_SINGLETON_PROPERTY_NAME = "micronautSingleton";

    protected DefaultBeanContext micronautContext;
    protected final List<Class<?>> micronautBeanQualifierTypes;
    private Environment environment;

    /**
     *
     * @param qualifierTypes The types associated with the
     *                   Micronaut beans which should be added to the
     *                   Spring application context.
     */
    public MicronautBeanProcessor(Class<?>... qualifierTypes) {
        this.micronautBeanQualifierTypes = Arrays.asList(qualifierTypes);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (environment != null) {
            String[] profiles = getProfiles();
            micronautContext = new DefaultApplicationContext(profiles) {
                DefaultEnvironment env = new DefaultEnvironment(profiles) {
                    @Override
                    public io.micronaut.context.env.Environment start() {
                        return this;
                    }

                    @Override
                    public io.micronaut.context.env.Environment stop() {
                        return this;
                    }

                    @Override
                    public boolean containsProperty(@Nullable String name) {
                        return environment.containsProperty(name);
                    }

                    @Override
                    public boolean containsProperties(@Nullable String name) {
                        return environment.containsProperty(name);
                    }

                    @Override
                    public <T> Optional<T> getProperty(@Nullable String name, ArgumentConversionContext<T> conversionContext) {
                        return Optional.ofNullable(environment.getProperty(name, conversionContext.getArgument().getType()));
                    }
                };

                @Override
                public io.micronaut.context.env.Environment getEnvironment() {
                    return env;
                }
            };
        } else {
            micronautContext = new DefaultApplicationContext();
        }
        micronautContext.start();

        micronautBeanQualifierTypes
                .forEach(micronautBeanQualifierType -> {
            Qualifier<Object> micronautBeanQualifier;
            if (micronautBeanQualifierType.isAnnotation()) {
                micronautBeanQualifier = Qualifiers.byStereotype((Class<? extends Annotation>) micronautBeanQualifierType);
            } else {
                micronautBeanQualifier = Qualifiers.byType(micronautBeanQualifierType);
            }
            micronautContext.getBeanDefinitions(micronautBeanQualifier)
                    .forEach(definition -> {
                        final BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder
                                .rootBeanDefinition(MicronautSpringBeanFactory.class.getName());
                        beanDefinitionBuilder.addPropertyValue(MICRONAUT_BEAN_TYPE_PROPERTY_NAME, definition.getBeanType());
                        beanDefinitionBuilder.addPropertyValue(MICRONAUT_CONTEXT_PROPERTY_NAME, micronautContext);
                        beanDefinitionBuilder.addPropertyValue(MICRONAUT_SINGLETON_PROPERTY_NAME, definition.isSingleton());
                        ((DefaultListableBeanFactory) beanFactory).registerBeanDefinition(definition.getName(), beanDefinitionBuilder.getBeanDefinition());
                    });
        });
    }

    private String[] getProfiles() {
        if (ArrayUtils.isNotEmpty(environment.getActiveProfiles())) {
            return environment.getActiveProfiles();
        } else {
            return environment.getDefaultProfiles();
        }
    }

    @Override
    public void destroy() throws Exception {
        if (micronautContext != null) {
            micronautContext.close();
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}

