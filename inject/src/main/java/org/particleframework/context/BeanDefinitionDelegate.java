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

import org.particleframework.context.annotation.EachProperty;
import org.particleframework.context.exceptions.BeanInstantiationException;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.value.ConvertibleValues;
import org.particleframework.core.type.Argument;
import org.particleframework.core.value.OptionalValues;
import org.particleframework.core.value.ValueResolver;
import org.particleframework.core.naming.NameResolver;
import org.particleframework.core.naming.Named;
import org.particleframework.inject.*;
import org.particleframework.inject.qualifiers.Qualifiers;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Stream;

/**
 * A delegate bean definition
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class BeanDefinitionDelegate<T> implements DelegatingBeanDefinition<T>, BeanFactory<T>, NameResolver, ValueResolver<String> {
    protected final BeanDefinition<T> definition;
    protected final Map<String, Object> attributes = new HashMap<>();

    public static final String PRIMARY_ATTRIBUTE = "org.particleframework.core.Primary";

    private BeanDefinitionDelegate(BeanDefinition<T> definition) {
        if(!(definition instanceof BeanFactory)) {
            throw new IllegalArgumentException("Delegate can only be used for bean factories");
        }
        this.definition = definition;
    }

    @Override
    public boolean isAbstract() {
        return definition.isAbstract();
    }

    BeanDefinition<T> getDelegate() {
        return definition;
    }

    @Override
    public Optional<Class<? extends Annotation>> getScope() {
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
        return get(EachProperty.class.getName(), Class.class) != null ||  definition.isIterable();
    }

    @Override
    public boolean isPrimary() {
        return definition.isPrimary() || get(PRIMARY_ATTRIBUTE, Boolean.class).orElse(false);
    }

    @Override
    public Class<T> getBeanType() {
        return definition.getBeanType();
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
    public boolean isEnabled(BeanContext beanContext) {
        return definition.isEnabled(beanContext);
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
    public T inject(BeanContext context, T bean) {
        return definition.inject(context, bean);
    }

    @Override
    public T inject(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
        return definition.inject(resolutionContext, context, bean);
    }

    @Override
    public Collection<ExecutableMethod<T, ?>> getExecutableMethods() {
        return definition.getExecutableMethods();
    }

    @Override
    public T build(BeanResolutionContext resolutionContext, BeanContext context, BeanDefinition<T> definition) throws BeanInstantiationException {
        resolutionContext.putAll(attributes);
        try {
            if(this.definition instanceof ParametrizedBeanFactory) {
                ParametrizedBeanFactory<T> parametrizedBeanFactory = (ParametrizedBeanFactory<T>) this.definition;
                Argument[] requiredArguments = parametrizedBeanFactory.getRequiredArguments();
                Object named = attributes.get(Named.class.getName());
                if(named != null) {
                    Map<String, Object> fulfilled = new LinkedHashMap<>();
                    for (Argument argument : requiredArguments) {
                        Class argumentType = argument.getType();
                        Optional result = ConversionService.SHARED.convert(named, argumentType);
                        String argumentName = argument.getName();
                        if(result.isPresent()) {
                            fulfilled.put(argumentName, result.get());
                        }
                        else {
                            // attempt bean lookup to full argument
                            Optional bean = context.findBean(argumentType, Qualifiers.byName(named.toString()));
                            if(bean.isPresent()) {
                                fulfilled.put(argumentName, bean.get());
                            }
                        }
                    }
                    return parametrizedBeanFactory.build(resolutionContext, context, definition, fulfilled);
                }
            }
            return ((BeanFactory<T>)this.definition).build(resolutionContext, context, definition);
        } finally {
            for (String key : attributes.keySet()) {
                resolutionContext.remove(key);
            }
        }
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BeanDefinitionDelegate<?> that = (BeanDefinitionDelegate<?>) o;
        return Objects.equals(definition, that.definition) &&
                Objects.equals(resolveName().orElse(null), that.resolveName().orElse(null));
    }

    @Override
    public int hashCode() {
        return Objects.hash(definition, resolveName().orElse(null));
    }



    static <T> BeanDefinitionDelegate<T> create(BeanDefinition<T> definition) {
        if(definition instanceof InitializingBeanDefinition || definition instanceof DisposableBeanDefinition) {
            if(definition instanceof ValidatedBeanDefinition) {
                return new LifeCycleValidatingDelegate<>(definition);
            }
            else {
                return new LifeCycleDelegate<>(definition);
            }
        }
        else if(definition instanceof ValidatedBeanDefinition) {
            return new ValidatingDelegate<>(definition);
        }
        return new BeanDefinitionDelegate<>(definition);
    }

    public BeanDefinition<T> getTarget() {
        return definition;
    }

    @Override
    public Optional<String> resolveName() {
        return get(Named.class.getName(), String.class);
    }

    public void put(String name, Object value) {
        this.attributes.put(name, value);
    }

    @Override
    public boolean hasDeclaredAnnotation(String annotation) {
        return getTarget().hasDeclaredAnnotation(annotation);
    }

    @Override
    public boolean hasAnnotation(String annotation) {
        return getTarget().hasAnnotation(annotation);
    }

    @Override
    public boolean hasStereotype(String annotation) {
        return getTarget().hasStereotype(annotation);
    }

    @Override
    public boolean hasDeclaredStereotype(String annotation) {
        return getTarget().hasDeclaredStereotype(annotation);
    }

    @Override
    public Set<String> getAnnotationNamesByStereotype(String stereotype) {
        return getTarget().getAnnotationNamesByStereotype(stereotype);
    }

    @Override
    public ConvertibleValues<Object> getValues(String annotation) {
        return getTarget().getValues(annotation);
    }

    @Override
    public <T1> OptionalValues<T1> getValues(String annotation, Class<T1> valueType) {
        return getTarget().getValues(annotation, valueType);
    }

    @Override
    public <T> Optional<T> getDefaultValue(String annotation, String member, Class<T> requiredType) {
        return getTarget().getDefaultValue(annotation, member,requiredType);
    }

    @Override
    public <T> Optional<T> getDefaultValue(Class<? extends Annotation> annotation, String member, Class<T> requiredType) {
        return getTarget().getDefaultValue(annotation, member,requiredType);
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return getTarget().getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return getTarget().getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return getTarget().getDeclaredAnnotations();
    }

    @Override
    public <T> Optional<T> get(String name, ArgumentConversionContext<T> conversionContext) {
        Object value = attributes.get(name);
        if(value != null && conversionContext.getArgument().getType().isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    interface ProxyInitializingBeanDefinition<T> extends DelegatingBeanDefinition<T>, InitializingBeanDefinition<T> {
        @Override
        default T initialize(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
            BeanDefinition<T> definition = getTarget();
            if(definition instanceof InitializingBeanDefinition) {
                return ((InitializingBeanDefinition<T>) definition).initialize(resolutionContext, context, bean);
            }
            return bean;
        }
    }

    interface ProxyDisosableBeanDefinition<T> extends DelegatingBeanDefinition<T>, DisposableBeanDefinition<T> {
        @Override
        default T dispose(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
            BeanDefinition<T> definition = getTarget();
            if(definition instanceof DisposableBeanDefinition) {
                return ((DisposableBeanDefinition<T>) definition).dispose(resolutionContext, context, bean);
            }
            return bean;
        }
    }

    interface ProxyValidatingBeanDefinitino<T> extends DelegatingBeanDefinition<T>, ValidatedBeanDefinition<T> {
        @Override
        default T validate(BeanResolutionContext resolutionContext, T instance) {
            BeanDefinition<T> definition = getTarget();
            if(definition instanceof ValidatedBeanDefinition) {
                return ((ValidatedBeanDefinition<T>) definition).validate(resolutionContext, instance);
            }
            return instance;
        }
    }

    private static class LifeCycleDelegate<T> extends BeanDefinitionDelegate<T> implements ProxyInitializingBeanDefinition<T>, ProxyDisosableBeanDefinition<T> {
        private LifeCycleDelegate(BeanDefinition<T> definition) {
            super(definition);
        }
    }
    private static class ValidatingDelegate<T> extends BeanDefinitionDelegate<T> implements ProxyValidatingBeanDefinitino<T> {
        private ValidatingDelegate(BeanDefinition<T> definition) {
            super(definition);
        }
    }
    private static class LifeCycleValidatingDelegate<T> extends LifeCycleDelegate<T> implements ProxyValidatingBeanDefinitino<T> {
        private LifeCycleValidatingDelegate(BeanDefinition<T> definition) {
            super(definition);
        }
    }
}
