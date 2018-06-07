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

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.BeanFactory;
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
    private Class beanDefinition;
    private Boolean present;
    private Class beanType;

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

    /**
     * @return The actual type of the component
     */
    @Override
    public Class getBeanType() {
        if (isPresent()) {
            Class beanType = this.beanType;
            if (beanType == null) {
                synchronized (this) { // double check
                    beanType = this.beanType;
                    if (beanType == null) {
                        beanType = GenericTypeUtils
                                .resolveInterfaceTypeArgument(beanDefinition, BeanFactory.class)
                                .orElse(null);
                        this.beanType = beanType;
                    }
                }
            }
            return beanType;
        }
        return null;
    }

    @Override
    public String getReplacesBeanTypeName() {
        return null; // no replacement semantics by default
    }

    /**
     * @return The loaded component definition
     */
    @Override
    public BeanDefinition load() {
        if ((present != null && present) || isPresent()) {
            try {
                return (BeanDefinition) beanDefinition.newInstance();
            } catch (Throwable e) {
                throw new BeanInstantiationException("Error loading bean definition [" + beanTypeName + "]: " + e.getMessage(), e);
            }
        } else {
            throw new BeanInstantiationException("Cannot load bean for type [" + beanTypeName + "]. The type is not present on the classpath");
        }
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
            loadType();
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

    private void loadType() {
        if (present == null && beanDefinition == null) {

            try {
                beanDefinition = Class.forName(beanDefinitionTypeName, false, getClass().getClassLoader());
                GenericTypeUtils.resolveInterfaceTypeArgument(beanDefinition, BeanFactory.class);
                present = true;
            } catch (TypeNotPresentException | ClassNotFoundException | NoClassDefFoundError e) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Bean definition for type [" + beanTypeName + "] not loaded since it is not on the classpath", e);
                }
                present = false;
            }
        }
    }
}
