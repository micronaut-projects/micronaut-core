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
package io.micronaut.runtime.bind;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.bind.BeanPropertyBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Implementation of {@link BeanPropertyBinder} using Micronaut's {@link BeanIntrospection}.
 *
 * @author Denis Stepanov
 * @since 2.1.0
 */
@Singleton
@Requires(missingBeans = BeanPropertyBinder.class)
public class IntrospectionBeanPropertyBinder extends AbstractBeanPropertyBinder {

    @Value("${micronaut.bean-property-binder.array-size-threshold:100}")
    private int arraySizeThreshold;

    @Override
    public <T2> T2 bind(Class<T2> type, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException {
        Argument<T2> argument = Argument.of(type);
        try {
            ConversionContext conversionContext = ConversionContext.of(argument);
            Map<String, Object> properties = buildMapObject(source);
            BeanIntrospection<T2> introspection = BeanIntrospection.getIntrospection(type);
            T2 instance = instantiate(introspection, properties, conversionContext);
            if (instance != null) {
                setProperties(introspection, properties, instance, conversionContext);
            }
            Optional<ConversionError> lastError = conversionContext.getLastError();
            if (lastError.isPresent()) {
                throw new ConversionErrorException(argument, lastError.get());
            }
            return instance;
        } catch (Exception e) {
            throw newConversionError(null, e);
        }
    }

    @Override
    public <T2> T2 bind(T2 instance, ArgumentConversionContext<T2> context, Set<? extends Map.Entry<? extends CharSequence, Object>> source) {
        try {
            Map<String, Object> properties = buildMapObject(source);
            BeanIntrospection<T2> introspection = (BeanIntrospection<T2>) BeanIntrospection.getIntrospection(instance.getClass());
            setProperties(introspection, properties, instance, context);
            return instance;
        } catch (Exception e) {
            context.reject(e);
        }
        return instance;
    }

    @Override
    public <T2> T2 bind(T2 instance, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException {
        try {
            ConversionContext conversionContext = ConversionContext.DEFAULT;
            Map<String, Object> properties = buildMapObject(source);
            BeanIntrospection<T2> introspection = (BeanIntrospection<T2>) BeanIntrospection.getIntrospection(instance.getClass());
            setProperties(introspection, properties, instance, conversionContext);
            Optional<ConversionError> lastError = conversionContext.getLastError();
            if (lastError.isPresent()) {
                throw new ConversionErrorException(Argument.of(instance.getClass()), lastError.get());
            }
            return instance;
        } catch (Exception e) {
            throw newConversionError(instance, e);
        }
    }

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, Map<CharSequence, ? super Object> source) {
        try {
            Map<String, Object> properties = buildMapObject(source.entrySet());
            BeanIntrospection<Object> introspection = BeanIntrospection.getIntrospection(context.getArgument().getType());
            Object instance = instantiate(introspection, properties, context);
            if (instance != null) {
                setProperties(introspection, properties, instance, context);
            }
            if (context.hasErrors()) {
                return bindingResultWithErrors(context);
            }
            return () -> Optional.of(instance);
        } catch (Exception e) {
            context.reject(e);
            return bindingResultWithErrors(context);
        }
    }

    private BindingResult<Object> bindingResultWithErrors(ArgumentConversionContext<Object> context) {
        return new BindingResult<Object>() {
            @Override
            public List<ConversionError> getConversionErrors() {
                return CollectionUtils.iterableToList(context);
            }

            @Override
            public boolean isSatisfied() {
                return false;
            }

            @Override
            public Optional<Object> getValue() {
                return Optional.empty();
            }
        };
    }

    private <T2> void setProperties(BeanIntrospection<T2> introspection, Map<String, Object> properties, T2 instance, ConversionContext context) {
        for (BeanProperty<T2, Object> beanProperty : introspection.getBeanProperties()) {
            Object value = properties.get(beanProperty.getName());
            if (value != null) {
                convertAndSet(instance, beanProperty, value, context);
            }
        }
    }

    private <I> I instantiate(BeanIntrospection<I> introspection, Map<String, Object> propertiesMap, ConversionContext context) {
        Argument<?>[] constructorArguments = introspection.getConstructorArguments();
        if (constructorArguments.length > 0) {
            List<Object> arguments = new ArrayList<>(constructorArguments.length);
            boolean rejected = false;
            for (Argument<?> argument : constructorArguments) {
                Object propertyValue = getPropertyValue(propertiesMap, argument);
                if (propertyValue != null) {
                    Object converted = ConversionService.SHARED.convert(propertyValue, argument.getType(), ConversionContext.of(argument)).orElseThrow(() ->
                            new ConversionErrorException(argument, context.getLastError()
                                    .orElse(() -> new IllegalArgumentException("Value [" + propertyValue + "] cannot be converted to type : " + argument.getType())))
                    );
                    arguments.add(converted);
                } else if (argument.isDeclaredNullable()) {
                    arguments.add(null);
                } else {
                    context.reject(new ConversionErrorException(argument, () -> new IllegalArgumentException("No Value found for argument " + argument.getName())));
                    rejected = true;
                }
            }
            if (rejected) {
                return null;
            }
            return introspection.instantiate(false, arguments.toArray());
        }
        return introspection.instantiate();
    }

    private Object getPropertyValue(Map<String, Object> propertiesMap, Argument<?> argument) {
        AnnotationValue<Annotation> jsonProperty = argument.getAnnotation("com.fasterxml.jackson.annotation.JsonProperty");
        if (jsonProperty != null) {
            String name = jsonProperty.stringValue().orElseGet(argument::getName);
            return propertiesMap.get(name);
        }
        return propertiesMap.get(argument.getName());
    }

    private <T> void convertAndSet(T instance, BeanProperty<T, Object> beanProperty, Object value, ConversionContext conversionContext) {
        Argument<Object> argument = beanProperty.asArgument();
        ArgumentConversionContext<Object> context = conversionContext.with(argument);
        Object converted = ConversionService.SHARED.convert(value, context).orElseThrow(() ->
                new ConversionErrorException(argument, context.getLastError()
                        .orElse(() -> new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + argument.getType())))
        );
        beanProperty.set(instance, converted);
    }

    private ConversionErrorException newConversionError(Object object, Exception e) {
        ConversionError conversionError = new ConversionError() {
            @Override
            public Exception getCause() {
                return e;
            }

            @Override
            public Optional<Object> getOriginalValue() {
                return Optional.ofNullable(object);
            }
        };
        Class type = object != null ? object.getClass() : Object.class;
        return new ConversionErrorException(Argument.of(type), conversionError);
    }

    @Override
    protected Object convertUnknownValue(Object o) {
        return ConversionService.SHARED.convert(o, Map.class);
    }

    @Override
    protected int getArraySizeThreshold() {
        return arraySizeThreshold;
    }

}
