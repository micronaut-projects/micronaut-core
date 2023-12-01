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

import io.micronaut.context.exceptions.BeanContextException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;

import java.util.Collections;
import java.util.Set;

/**
 * An uninitialized and unloaded component definition with basic information available regarding its requirements.
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 3.0
 */
@Internal
public abstract class AbstractInitializableBeanDefinitionReference<T> extends AbstractBeanContextConditional implements BeanDefinitionReference<T> {

    private final String beanTypeName;
    private final String beanDefinitionTypeName;
    private final AnnotationMetadata annotationMetadata;
    private final boolean isPrimary;
    private final boolean isContextScope;
    private final boolean isConditional;
    private final boolean isContainerType;
    private final boolean isSingleton;
    private final boolean isConfigurationProperties;
    private final boolean hasExposedTypes;
    private final boolean requiresMethodProcessing;
    private final boolean isProxiedBean;
    private final boolean isProxyTarget;

    private Boolean present;
    private Set<Class<?>> exposedTypes;

    /**
     * @param beanTypeName              The bean type name
     * @param beanDefinitionTypeName    The bean definition type name
     * @param annotationMetadata        The annotationMetadata
     * @param isPrimary                 Is primary bean?
     * @param isContextScope            Is context scope?
     * @param isConditional             Is conditional? = No @Requires
     * @param isContainerType           Is container type?
     * @param isSingleton               Is singleton?
     * @param isConfigurationProperties Is configuration properties?
     * @param hasExposedTypes           Has exposed types?
     * @param requiresMethodProcessing  Is requires method processing?
     */
    public AbstractInitializableBeanDefinitionReference(String beanTypeName, String beanDefinitionTypeName, AnnotationMetadata annotationMetadata,
                                                        boolean isPrimary, boolean isContextScope, boolean isConditional,
                                                        boolean isContainerType, boolean isSingleton, boolean isConfigurationProperties,
                                                        boolean hasExposedTypes, boolean requiresMethodProcessing) {
        this(beanTypeName, beanDefinitionTypeName, annotationMetadata, isPrimary, isContextScope, isConditional, isContainerType, isSingleton, isConfigurationProperties, hasExposedTypes, requiresMethodProcessing, false, false);
    }

    /**
     * @param beanTypeName              The bean type name
     * @param beanDefinitionTypeName    The bean definition type name
     * @param annotationMetadata        The annotationMetadata
     * @param isPrimary                 Is primary bean?
     * @param isContextScope            Is context scope?
     * @param isConditional             Is conditional? = No @Requires
     * @param isContainerType           Is container type?
     * @param isSingleton               Is singleton?
     * @param isConfigurationProperties Is configuration properties?
     * @param hasExposedTypes           Has exposed types?
     * @param requiresMethodProcessing  Is requires method processing?
     * @param isProxiedBean             Is the bean proxied
     * @param isProxyTarget             Is the bean a retained proxy target
     */
    protected AbstractInitializableBeanDefinitionReference(String beanTypeName, String beanDefinitionTypeName, AnnotationMetadata annotationMetadata,
                                                        boolean isPrimary, boolean isContextScope, boolean isConditional,
                                                        boolean isContainerType, boolean isSingleton, boolean isConfigurationProperties,
                                                        boolean hasExposedTypes, boolean requiresMethodProcessing, boolean isProxiedBean, boolean isProxyTarget) {
        this.beanTypeName = beanTypeName;
        this.beanDefinitionTypeName = beanDefinitionTypeName;
        this.annotationMetadata = annotationMetadata;
        this.isPrimary = isPrimary;
        this.isContextScope = isContextScope;
        this.isConditional = isConditional;
        this.isContainerType = isContainerType;
        this.isSingleton = isSingleton;
        this.isConfigurationProperties = isConfigurationProperties;
        this.hasExposedTypes = hasExposedTypes;
        this.requiresMethodProcessing = requiresMethodProcessing;
        this.isProxiedBean = isProxiedBean;
        this.isProxyTarget = isProxyTarget;
    }

    @Override
    public boolean isProxyTarget() {
        return isProxyTarget;
    }

    @Override
    public boolean isProxiedBean() {
        return isProxiedBean;
    }

    @Override
    public String getName() {
        return beanTypeName;
    }

    @Override
    public String getBeanDefinitionName() {
        return beanDefinitionTypeName;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public boolean isPrimary() {
        return isPrimary;
    }

    @Override
    public boolean isSingleton() {
        return isSingleton;
    }

    @Override
    public boolean isConfigurationProperties() {
        return isConfigurationProperties;
    }

    @Override
    public boolean isContainerType() {
        return isContainerType;
    }

    @Override
    public boolean isContextScope() {
        return isContextScope;
    }

    @Override
    public boolean requiresMethodProcessing() {
        return requiresMethodProcessing;
    }

    @Override
    @NonNull
    public final Set<Class<?>> getExposedTypes() {
        if (!hasExposedTypes) {
            return Collections.EMPTY_SET;
        }
        if (exposedTypes == null) {
            this.exposedTypes = BeanDefinitionReference.super.getExposedTypes();
        }
        return this.exposedTypes;
    }

    @Override
    public BeanDefinition load(BeanContext context) {
        BeanDefinition definition = load();
        if (context instanceof DefaultApplicationContext applicationContext
            && definition instanceof EnvironmentConfigurable environmentConfigurable) {
            // Performance optimization to check for the actual class to avoid the type-check pollution
            environmentConfigurable.configure(applicationContext.getEnvironment());
        } else if (context instanceof ApplicationContext applicationContext && definition instanceof EnvironmentConfigurable environmentConfigurable) {
            environmentConfigurable.configure(applicationContext.getEnvironment());
        }
        if (definition instanceof BeanContextConfigurable ctxConfigurable) {
            ctxConfigurable.configure(context);
        }
        return definition;
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
                    if (ConditionLog.LOG.isTraceEnabled()) {
                        ConditionLog.LOG.trace("Bean definition for type [{}] not loaded since it is not on the classpath", beanTypeName, e);
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
    public boolean isEnabled(BeanContext context) {
        return isPresent() && (!isConditional || super.isEnabled(context, null));
    }

    @Override
    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
        return isPresent() && (!isConditional || super.isEnabled(context, resolutionContext));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AbstractInitializableBeanDefinitionReference that = (AbstractInitializableBeanDefinitionReference) o;
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
