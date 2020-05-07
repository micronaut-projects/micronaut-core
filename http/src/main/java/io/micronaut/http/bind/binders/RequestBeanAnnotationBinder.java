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
package io.micronaut.http.bind.binders;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.annotation.AbstractAnnotatedArgumentBinder;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.RequestBean;
import io.micronaut.http.bind.RequestBinderRegistry;

/**
 * Used to bind Bindable parameters to a Bean object.
 *
 * @author Anze Sodja
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
            List<BeanProperty<T, Object>> bindableProperties = introspection.getBeanProperties().stream()
                    .filter(p -> p.getType().isAssignableFrom(HttpRequest.class) || p.hasStereotype(Bindable.class))
                    .collect(Collectors.toList());

            if (introspection.getConstructorArguments().length > 0) {
                // Handle injection with Constructor or @Creator
                Object[] argumentValues = Arrays.stream(introspection.getConstructorArguments())
                        .map(a -> bindArgument((Argument<Object>) a, bindableProperties, context, source))
                        .toArray();
                return () -> Optional.of(introspection.instantiate(false, argumentValues));
            } else {
                // Handle injection with setters, we checked that all values are writable at compile time
                T bean = introspection.instantiate();
                for (BeanProperty<T, Object> property : bindableProperties) {
                    ArgumentConversionContext<Object> conversionContext = propertyConversionContext(property, property.getType(), context);
                    Optional<Object> bindableResult = getBindableResult(conversionContext, source);
                    property.set(bean, property.getType().isAssignableFrom(Optional.class)
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

    private Object bindArgument(Argument<Object> argument, List<BeanProperty<T, Object>> bindableProperties,
            ArgumentConversionContext<T> parentContext, HttpRequest<?> source) {
        if (argument.getType().isAssignableFrom(HttpRequest.class)) {
            return source;
        }
        Optional<BeanProperty<T, Object>> bindablePropertyForArgument = bindableProperties.stream()
                .filter(p -> p.getName().equals(argument.getName()))
                .findFirst();
        if (bindablePropertyForArgument.isPresent()) {
            ArgumentConversionContext<Object> conversionContext = propertyConversionContext(bindablePropertyForArgument.get(), argument.getType(), parentContext);
            Optional<Object> result = getBindableResult(conversionContext, source);
            return argument.getType().isAssignableFrom(Optional.class) ? result : result.orElse(null);
        } else {
            return argument.getType().isAssignableFrom(Optional.class) ? Optional.empty() : null;
        }
    }

    private Optional<Object> getBindableResult(ArgumentConversionContext<Object> conversionContext, HttpRequest<?> source) {
        if (conversionContext.getArgument().getType().isAssignableFrom(HttpRequest.class)) {
            return Optional.of(source);
        }
        Argument<Object> argument = conversionContext.getArgument();
        Optional<ArgumentBinder<Object, HttpRequest<?>>> binder = requestBinderRegistry.findArgumentBinder(conversionContext.getArgument(), source);
        if (!binder.isPresent()) {
            throw new UnsatisfiedArgumentException(argument);
        }
        BindingResult<Object> result = binder.get().bind(conversionContext, source);
        if (!result.isSatisfied() || !result.getConversionErrors().isEmpty()) {
            List<ConversionError> errors = result.getConversionErrors();
            throw new ConversionErrorException(argument, errors.get(errors.size() - 1));
        }
        if (!result.isPresentAndSatisfied() && !argument.isNullable() && !argument.getType().isAssignableFrom(Optional.class)) {
            throw new UnsatisfiedArgumentException(conversionContext.getArgument());
        }
        return result.getValue();
    }

    private ArgumentConversionContext<Object> propertyConversionContext(BeanProperty<T, Object> property,
            Class<Object> type, ArgumentConversionContext<T> parentContext) {
        Argument<Object> argument = Argument.of(type, property.getName(), property.getAnnotationMetadata());
        return ConversionContext.of(argument, parentContext.getLocale(), parentContext.getCharset());
    }

}
