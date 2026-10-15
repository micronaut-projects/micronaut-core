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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.naming.Named;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpParameters;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.RequestBean;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Used to bind Bindable parameters to a Bean object.
 * NOTE: The binder is annotating as postponed to allow injecting values added by filters.

 * @author Anze Sodja
 * @author graemerocher
 * @since 2.0
 * @see RequestBean
 * @param <T>
 */
public class RequestBeanAnnotationBinder<T> implements AnnotatedRequestArgumentBinder<RequestBean, T>, PostponedRequestArgumentBinder<T> {

    private final RequestBinderRegistry requestBinderRegistry;

    /**
     * @param requestBinderRegistry Original request binder registry
     */
    public RequestBeanAnnotationBinder(RequestBinderRegistry requestBinderRegistry) {
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

            List<Member<T>> members;
            boolean pending = false;
            Argument<?>[] constructorArguments = introspection.getConstructorArguments();
            boolean constructor = constructorArguments.length > 0;
            if (constructor) {
                // Handle injection with Constructor or @Creator
                members = new ArrayList<>(constructorArguments.length);
                for (Argument<?> value : constructorArguments) {
                    @SuppressWarnings("unchecked")
                    Argument<Object> constructorArgument = (Argument<Object>) value;
                    BeanProperty<T, Object> bp = beanProperties.get(constructorArgument.getName());
                    Argument<Object> argumentToBind;
                    if (bp != null) {
                        argumentToBind = bp.asArgument();
                    } else {
                        argumentToBind = constructorArgument;
                    }
                    Member<T> member = new Member<>(argumentToBind, constructorArgument.isOptional(), null);
                    pending |= member.bind(this, source);
                    members.add(member);
                }
            } else {
                // Handle injection with setters, we checked that all values are writable at compile time
                members = new ArrayList<>(beanProperties.size());
                for (BeanProperty<T, Object> property : beanProperties.values()) {
                    Argument<Object> propertyArgument = property.asArgument();
                    Member<T> member = new Member<>(propertyArgument, propertyArgument.isOptional(), property);
                    pending |= member.bind(this, source);
                    members.add(member);
                }
            }
            if (!pending) {
                return instantiate(argument, introspection, constructor, members);
            }
            // a member waits for the request, e.g. the form of a body that is still arriving: the
            // bean is created once the route waited for it, like an argument of the route
            return new PendingRequestBindingResult<>() {
                private @Nullable BindingResult<T> bound;

                @Override
                public boolean isPending() {
                    for (Member<T> member : members) {
                        if (member.isPending()) {
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                public Optional<T> getValue() {
                    if (isPending()) {
                        return Optional.empty();
                    }
                    BindingResult<T> result = bound;
                    if (result == null) {
                        for (Member<T> member : members) {
                            member.complete(RequestBeanAnnotationBinder.this);
                        }
                        result = instantiate(argument, introspection, constructor, members);
                        bound = result;
                    }
                    return result.getValue();
                }
            };
        } else {
            //noinspection unchecked
            return BindingResult.EMPTY;
        }
    }

    /**
     * Create the bean from the values of its members, which are all bound.
     */
    private BindingResult<T> instantiate(Argument<T> argument, BeanIntrospection<T> introspection, boolean constructor, List<Member<T>> members) {
        boolean bindingFound = false;
        for (Member<T> member : members) {
            if (member.value().isPresent() && !isContextType(member.argument.getType())) {
                bindingFound = true;
            }
        }
        if (!bindingFound && argument.isNullable()) {
            return BindingResult.empty();
        }
        if (constructor) {
            Object[] argumentValues = new Object[members.size()];
            for (int i = 0; i < argumentValues.length; i++) {
                argumentValues[i] = members.get(i).beanValue();
            }
            return () -> Optional.of(introspection.instantiate(false, argumentValues));
        }
        T bean = introspection.instantiate();
        for (Member<T> member : members) {
            Objects.requireNonNull(member.property, "property").set(bean, member.beanValue());
        }
        return () -> Optional.of(bean);
    }

    private BindingResult<Object> bindMember(HttpRequest<?> source, Argument<Object> argument) {
        ArgumentConversionContext<Object> conversionContext = ConversionContext.of(
                argument,
                source.getLocale().orElse(Locale.getDefault()),
                source.getCharacterEncoding()
        );
        Optional<ArgumentBinder<Object, HttpRequest<?>>> binder = requestBinderRegistry.findArgumentBinder(argument);
        if (binder.isEmpty()) {
            throw new UnsatisfiedArgumentException(argument);
        }
        return binder.get().bind(conversionContext, source);
    }

    private Optional<Object> memberValue(Argument<Object> argument, BindingResult<Object> result) {
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

    private boolean isContextType(Class<?> type) {
        // Using the classes added in byType map in DefaultRequestBinderRegistry
        return HttpHeaders.class.isAssignableFrom(type) ||
            HttpRequest.class.isAssignableFrom(type)    ||
            HttpParameters.class.isAssignableFrom(type) ||
            Cookies.class.isAssignableFrom(type)        ||
            Cookie.class.isAssignableFrom(type);

    }

    /**
     * A member of a request bean: an argument of its constructor, or a property.
     *
     * @param <B> The type of the bean
     */
    private static final class Member<B> {
        final Argument<Object> argument;
        final boolean optional;
        final @Nullable BeanProperty<B, Object> property;
        private @Nullable PendingRequestBindingResult<Object> pending;
        private Optional<Object> value = Optional.empty();
        private boolean bound;

        Member(Argument<Object> argument, boolean optional, @Nullable BeanProperty<B, Object> property) {
            this.argument = argument;
            this.optional = optional;
            this.property = property;
        }

        /**
         * Bind the member: its value is read now, unless the binding waits for the request.
         *
         * @return Whether the binding waits for the request
         */
        boolean bind(RequestBeanAnnotationBinder<B> binder, HttpRequest<?> source) {
            BindingResult<Object> result = binder.bindMember(source, argument);
            if (result instanceof PendingRequestBindingResult<Object> pendingResult && pendingResult.isPending()) {
                pending = pendingResult;
                return true;
            }
            value = binder.memberValue(argument, result);
            bound = true;
            return false;
        }

        boolean isPending() {
            PendingRequestBindingResult<Object> result = pending;
            return result != null && result.isPending();
        }

        /**
         * Read the value of a binding that waited for the request.
         */
        void complete(RequestBeanAnnotationBinder<B> binder) {
            PendingRequestBindingResult<Object> result = pending;
            if (result != null && !bound) {
                value = binder.memberValue(argument, result);
                bound = true;
            }
        }

        Optional<Object> value() {
            if (!bound) {
                throw new IllegalStateException("The member " + argument + " is not bound yet");
            }
            return value;
        }

        /**
         * @return The value the bean is created with
         */
        @Nullable Object beanValue() {
            Optional<Object> bound = value();
            return optional ? bound : bound.orElse(null);
        }
    }

}
