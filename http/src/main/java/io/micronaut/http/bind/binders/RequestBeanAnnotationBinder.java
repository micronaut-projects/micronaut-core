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
package io.micronaut.http.bind.binders;

import java.util.*;
import java.util.stream.Collectors;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.annotation.AbstractAnnotatedArgumentBinder;
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.naming.Named;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.RequestBean;
import io.micronaut.http.bind.RequestBinderRegistry;

/**
 * Used to bind Bindable parameters to a Bean object.
 *
 * @author Anze Sodja
 * @author graemerocher
 * @since 2.0
 * @see RequestBean
 * @param <T>
 */
public class RequestBeanAnnotationBinder<T> extends AbstractAnnotatedArgumentBinder<RequestBean, T, HttpRequest<?>>
        implements AnnotatedRequestArgumentBinder<RequestBean, T> {

    private final RequestBinderRegistry requestBinderRegistry;

    /**
     * @param requestBinderRegistry Original request binder registry
     * @param conversionService The conversion service
     */
    public RequestBeanAnnotationBinder(RequestBinderRegistry requestBinderRegistry, ConversionService<?> conversionService) {
        super(conversionService);
        this.requestBinderRegistry = requestBinderRegistry;
    }

    @Override
    public Class<RequestBean> getAnnotationType() {
        return RequestBean.class;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        Argument<T> argument = context.getArgument();
        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
        boolean hasAnnotation = annotationMetadata.hasAnnotation(RequestBean.class);

        if (hasAnnotation) {
            BeanIntrospection<T> introspection = BeanIntrospection.getIntrospection(context.getArgument().getType());
            Map<String, BeanProperty<T, Object>> beanProperties = introspection.getBeanProperties().stream()
                    .collect(Collectors.toMap(Named::getName, p -> p));

            if (introspection.getConstructorArguments().length > 0) {
                // Handle injection with Constructor or @Creator
                Argument<?>[] constructorArguments = introspection.getConstructorArguments();
                Object[] argumentValues = new Object[constructorArguments.length];
                for (int i = 0; i < constructorArguments.length; i++) {
                    @SuppressWarnings("unchecked")
                    Argument<Object> constructorArgument = (Argument<Object>) constructorArguments[i];
                    BeanProperty<T, Object> bp = beanProperties.get(constructorArgument.getName());
                    Argument<Object> argumentToBind;
                    if (bp != null) {
                        argumentToBind = bp.asArgument();
                    } else {
                        argumentToBind = constructorArgument;
                    }
                    Optional<Object> bindableResult = getBindableResult(source, argumentToBind);
                    argumentValues[i] = constructorArgument.isOptional() ? bindableResult : bindableResult.orElse(null);
                }
                return () -> Optional.of(introspection.instantiate(false, argumentValues));
            } else {
                // Handle injection with setters, we checked that all values are writable at compile time
                T bean = introspection.instantiate();
                for (BeanProperty<T, Object> property : beanProperties.values()) {
                    Argument<Object> propertyArgument = property.asArgument();
                    Optional<Object> bindableResult = getBindableResult(source, propertyArgument);
                    property.set(bean, propertyArgument.isOptional()
                            ? bindableResult
                            : bindableResult.orElse(null));
                }
                return () -> Optional.of(bean);
            }
        } else {
            //noinspection unchecked
            return BindingResult.EMPTY;
        }
    }

    private Optional<Object> getBindableResult(HttpRequest<?> source, Argument<Object> argument) {
        ArgumentConversionContext<Object> conversionContext = ConversionContext.of(
                argument,
                source.getLocale().orElse(Locale.getDefault()),
                source.getCharacterEncoding()
        );
        return getBindableResult(conversionContext, source);
    }

    private Optional<Object> getBindableResult(ArgumentConversionContext<Object> conversionContext, HttpRequest<?> source) {
        Argument<Object> argument = conversionContext.getArgument();
        Optional<ArgumentBinder<Object, HttpRequest<?>>> binder = requestBinderRegistry.findArgumentBinder(argument, source);
        if (!binder.isPresent()) {
            throw new UnsatisfiedArgumentException(argument);
        }
        BindingResult<Object> result = binder.get().bind(conversionContext, source);
        if (!result.isSatisfied() || !result.getConversionErrors().isEmpty()) {
            List<ConversionError> errors = result.getConversionErrors();
            if (!errors.isEmpty()) {
                throw new ConversionErrorException(argument, errors.iterator().next());
            }
        }
        if (!result.isPresentAndSatisfied() && !argument.isNullable() && !argument.getType().isAssignableFrom(Optional.class)) {
            throw new UnsatisfiedArgumentException(argument);
        }
        return result.getValue();
    }

}
