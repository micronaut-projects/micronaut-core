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
package io.micronaut.context;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.exceptions.BeanContextException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassLoadingReporter;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An uninitialized and unloaded component definition with basic information available regarding its requirements.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractBeanDefinitionReference extends AbstractBeanContextConditional implements BeanDefinitionReference {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractBeanDefinitionReference.class);
    private final String beanTypeName;
    private final String beanDefinitionTypeName;
    private Boolean present;

    /**
     * @param beanTypeName           The bean type name
     * @param beanDefinitionTypeName The bean definition type name
     */
    public AbstractBeanDefinitionReference(String beanTypeName, String beanDefinitionTypeName) {
        this.beanTypeName = beanTypeName;
        this.beanDefinitionTypeName = beanDefinitionTypeName;
    }

    @Override
    public boolean isPrimary() {
        return getAnnotationMetadata().hasAnnotation(Primary.class);
    }

    @Override
    public String getName() {
        return beanTypeName;
    }

    @Override
    public BeanDefinition load(BeanContext context) {
        BeanDefinition definition = load();
        if (context instanceof ApplicationContext && definition instanceof EnvironmentConfigurable) {
            ((EnvironmentConfigurable) definition).configure(((ApplicationContext) context).getEnvironment());
        }
        return definition;
    }

    @Override
    public boolean isContextScope() {
        return getAnnotationMetadata().hasDeclaredStereotype(Context.class);
    }

    @Override
    public String getBeanDefinitionName() {
        return beanDefinitionTypeName;
    }

    @Override
    public boolean isPresent() {
        if (present == null) {
            try {
                getBeanDefinitionType();
                getBeanType();
                present = true;
            } catch (Throwable e) {
                if (e instanceof TypeNotPresentException || e instanceof ClassNotFoundException || e instanceof NoClassDefFoundError) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Bean definition for type [" + beanTypeName + "] not loaded since it is not on the classpath", e);
                    }
                } else {
                    throw new BeanContextException("Unexpected error loading bean definition [" + beanDefinitionTypeName + "]: " + e.getMessage(), e);
                }
                present = false;
            }
        }
        return present;
    }

    @Override
    public boolean isEnabled(BeanContext beanContext) {
        return isPresent() && super.isEnabled(beanContext);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AbstractBeanDefinitionReference that = (AbstractBeanDefinitionReference) o;

        return beanDefinitionTypeName.equals(that.beanDefinitionTypeName);
    }

    @Override
    public String toString() {
        return beanDefinitionTypeName;
    }

    @Override
    public int hashCode() {
        return beanDefinitionTypeName.hashCode();
    }

    /**
     * Implementors should provide an implementation of this method that returns the bean definition type.
     *
     * @return The bean definition type.
     */
    protected abstract Class<? extends BeanDefinition<?>> getBeanDefinitionType();
}
