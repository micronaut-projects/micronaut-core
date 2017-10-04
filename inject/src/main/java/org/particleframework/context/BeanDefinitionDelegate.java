/*
 * Copyright 2017 original authors
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
package org.particleframework.context;

import org.particleframework.context.exceptions.BeanInstantiationException;
import org.particleframework.core.convert.MutableConvertibleValuesMap;
import org.particleframework.inject.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A delegate bean definition
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class BeanDefinitionDelegate<T> extends MutableConvertibleValuesMap<Object> implements BeanDefinition<T>, BeanFactory<T> {
    private final BeanDefinition<T> definition;

    BeanDefinitionDelegate(BeanDefinition<T> definition) {
        if(!(definition instanceof BeanFactory)) {
            throw new IllegalArgumentException("Delegate can only be used for bean factories");
        }
        this.definition = definition;
    }

    @Override
    public Annotation getScope() {
        return definition.getScope();
    }

    @Override
    public boolean isSingleton() {
        return definition.isSingleton();
    }

    @Override
    public boolean isProvided() {
        return definition.isProvided();
    }

    @Override
    public boolean isIterable() {
        return definition.isIterable();
    }

    @Override
    public boolean isPrimary() {
        return definition.isPrimary();
    }

    @Override
    public Class<T> getType() {
        return definition.getType();
    }

    @Override
    public ConstructorInjectionPoint<T> getConstructor() {
        return definition.getConstructor();
    }

    @Override
    public Collection<Class> getRequiredComponents() {
        return definition.getRequiredComponents();
    }

    @Override
    public Collection<MethodInjectionPoint> getInjectedMethods() {
        return definition.getInjectedMethods();
    }

    @Override
    public Collection<FieldInjectionPoint> getInjectedFields() {
        return definition.getInjectedFields();
    }

    @Override
    public Collection<MethodInjectionPoint> getPostConstructMethods() {
        return definition.getPostConstructMethods();
    }

    @Override
    public Collection<MethodInjectionPoint> getPreDestroyMethods() {
        return definition.getPreDestroyMethods();
    }

    @Override
    public String getName() {
        return definition.getName();
    }

    @Override
    public <R> Optional<ExecutableMethod<T, R>> findMethod(String name, Class[] argumentTypes) {
        return definition.findMethod(name, argumentTypes);
    }

    @Override
    public <R> Stream<ExecutableMethod<T, R>> findPossibleMethods(String name) {
        return definition.findPossibleMethods(name);
    }

    @Override
    public T build(BeanResolutionContext resolutionContext, BeanContext context, BeanDefinition<T> definition) throws BeanInstantiationException {
        resolutionContext.putAll(this);
        return ((BeanFactory<T>)this.definition).build(resolutionContext, context, definition);
    }

    @Override
    public AnnotatedElement[] getAnnotatedElements() {
        return definition.getAnnotatedElements();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BeanDefinitionDelegate<?> that = (BeanDefinitionDelegate<?>) o;

        return definition.equals(that.definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }
}
