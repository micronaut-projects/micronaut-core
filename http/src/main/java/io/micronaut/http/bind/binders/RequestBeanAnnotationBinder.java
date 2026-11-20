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
    private final @Nullable Prepared<T> prepared;

    /**
     * @param requestBinderRegistry Original request binder registry
     */
    public RequestBeanAnnotationBinder(RequestBinderRegistry requestBinderRegistry) {
        this.requestBinderRegistry = requestBinderRegistry;
        this.prepared = null;
    }

    private RequestBeanAnnotationBinder(RequestBinderRegistry registry, Prepared<T> prepared) {
        requestBinderRegistry = registry;
        this.prepared = prepared;
    }

    @Override
    public Class<RequestBean> getAnnotationType() {
        return RequestBean.class;
    }

    @Override
    public RequestArgumentBinder<T> createSpecific(Argument<T> argument) {
        if (getClass() != RequestBeanAnnotationBinder.class || !argument.getAnnotationMetadata().hasAnnotation(RequestBean.class)) {
            return this;
        }
        return new RequestBeanAnnotationBinder<>(requestBinderRegistry, prepare(argument));
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        Argument<T> argument = context.getArgument();
        if (!argument.getAnnotationMetadata().hasAnnotation(RequestBean.class)) {
            return BindingResult.empty();
        }
        Prepared<T> plan = prepared == null ? prepare(argument) : prepared;
        return new BoundBean(argument, plan, source).result();
    }

    private Prepared<T> prepare(Argument<T> argument) {
        BeanIntrospection<T> introspection = BeanIntrospection.getIntrospection(argument.getType());
        Map<String, BeanProperty<T, Object>> properties = introspection.getBeanProperties().stream()
            .collect(Collectors.toMap(Named::getName, p -> p));
        Argument<?>[] constructorArguments = introspection.getConstructorArguments();
        boolean constructor = constructorArguments.length > 0;
        List<Member<T>> members = new ArrayList<>(constructor ? constructorArguments.length : properties.size());
        if (constructor) {
            for (Argument<?> value : constructorArguments) {
                Argument<Object> constructorArgument = (Argument<Object>) value;
                BeanProperty<T, Object> property = properties.get(constructorArgument.getName());
                Argument<Object> memberArgument = property == null ? constructorArgument : property.asArgument();
                members.add(member(memberArgument, constructorArgument.isOptional(), null));
            }
        } else {
            for (BeanProperty<T, Object> property : properties.values()) {
                Argument<Object> memberArgument = property.asArgument();
                members.add(member(memberArgument, memberArgument.isOptional(), property));
            }
        }
        return new Prepared<>(introspection, constructor, List.copyOf(members));
    }

    private Member<T> member(Argument<Object> argument, boolean optional, @Nullable BeanProperty<T, Object> property) {
        ArgumentBinder<Object, HttpRequest<?>> binder = requestBinderRegistry.findArgumentBinder(argument)
            .orElseThrow(() -> new UnsatisfiedArgumentException(argument));
        return new Member<>(argument, optional, property, binder);
    }

    private static boolean isContextType(Class<?> type) {
        // Using the classes added in byType map in DefaultRequestBinderRegistry
        return HttpHeaders.class.isAssignableFrom(type) ||
            HttpRequest.class.isAssignableFrom(type) ||
            HttpParameters.class.isAssignableFrom(type) ||
            Cookies.class.isAssignableFrom(type) ||
            Cookie.class.isAssignableFrom(type);
    }

    private record Prepared<B>(BeanIntrospection<B> introspection, boolean constructor, List<Member<B>> members) {
    }

    private record Member<B>(Argument<Object> argument, boolean optional, @Nullable BeanProperty<B, Object> property,
                             ArgumentBinder<Object, HttpRequest<?>> binder) {
    }

    /**
     * Only values and results that wait for this request are mutable.
     */
    private final class BoundBean implements PendingRequestBindingResult<T> {
        private final Argument<T> argument;
        private final Prepared<T> plan;
        private final @Nullable Object[] values;
        private BindingResult<?> @Nullable [] pending;
        private boolean bindingFound;
        private @Nullable BindingResult<T> bound;

        BoundBean(Argument<T> argument, Prepared<T> plan, HttpRequest<?> source) {
            this.argument = argument;
            this.plan = plan;
            values = new Object[plan.members.size()];
            for (int i = 0; i < values.length; i++) {
                Member<T> member = plan.members.get(i);
                ArgumentConversionContext<Object> context = ConversionContext.of(member.argument,
                    source.getLocale().orElse(Locale.getDefault()), source.getCharacterEncoding());
                BindingResult<Object> result = member.binder.bind(context, source);
                if (result.isPending()) {
                    if (pending == null) {
                        pending = new BindingResult<?>[values.length];
                    }
                    pending[i] = result;
                } else {
                    setValue(i, result);
                }
            }
        }

        BindingResult<T> result() {
            return isPending() ? this : complete();
        }

        @Override
        public boolean isPending() {
            if (pending != null) {
                for (BindingResult<?> result : pending) {
                    if (result != null && result.isPending()) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void setValue(int index, BindingResult<?> result) {
            Member<T> member = plan.members.get(index);
            List<ConversionError> errors = result.getConversionErrors();
            if (!errors.isEmpty()) {
                throw new ConversionErrorException(member.argument, errors.getFirst());
            }
            boolean satisfied = result.isSatisfied();
            Optional<?> value = result.getValue();
            if ((!satisfied || value.isEmpty()) && !member.argument.isNullable() && !member.argument.isOptional()) {
                throw new UnsatisfiedArgumentException(member.argument);
            }
            Object beanValue = value.orElse(null);
            boolean completeOptional = member.optional && result.isConvertedToArgumentType() && beanValue instanceof Optional<?>;
            boolean present = completeOptional && beanValue instanceof Optional<?> optional ? optional.isPresent() : value.isPresent();
            bindingFound |= present && !isContextType(member.argument.getType());
            values[index] = member.optional && !completeOptional ? value : beanValue;
        }

        private BindingResult<T> complete() {
            if (bound != null) {
                return bound;
            }
            if (pending != null) {
                for (int i = 0; i < pending.length; i++) {
                    BindingResult<?> result = pending[i];
                    if (result != null) {
                        setValue(i, result);
                        pending[i] = null;
                    }
                }
            }
            if (!bindingFound && argument.isNullable()) {
                bound = BindingResult.empty();
            } else if (plan.constructor) {
                bound = BindingResult.converted(plan.introspection.instantiate(false, values));
            } else {
                T bean = plan.introspection.instantiate();
                for (int i = 0; i < values.length; i++) {
                    Objects.requireNonNull(plan.members.get(i).property, "property").set(bean, values[i]);
                }
                bound = BindingResult.converted(bean);
            }
            return bound;
        }

        @Override
        public Optional<T> getValue() {
            return isPending() ? Optional.empty() : complete().getValue();
        }

        @Override
        public boolean isConvertedToArgumentType() {
            return !isPending() && complete().isConvertedToArgumentType();
        }
    }
}
