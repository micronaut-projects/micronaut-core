/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.beans.KotlinExecutableMethodUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The default {@link MethodBasedRouteInfo} implementation.
 *
 * @param <T> The target
 * @param <R> The result
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
sealed class DefaultMethodBasedRouteInfo<T, R> extends DefaultRouteInfo<R> implements MethodBasedRouteInfo<T, R>
    permits DefaultRequestMatcher {

    private static final RequestArgumentBinder[] ZERO_BINDERS = new RequestArgumentBinder[0];
    private final MethodExecutionHandle<T, R> targetMethod;
    private final String[] argumentNames;
    private final Map<String, Argument<?>> requiredInputs;
    private final boolean isVoid;
    private final Optional<Argument<?>> optionalBodyArgument;
    private final Optional<Argument<?>> optionalFullBodyArgument;
    private final MessageBodyReader<?> messageBodyReader;

    private RequestArgumentBinder<Object>[] argumentBinders;
    private final boolean needsBody;

    public DefaultMethodBasedRouteInfo(MethodExecutionHandle<T, R> targetMethod,
                                       @Nullable
                                       Argument<?> bodyArgument,
                                       @Nullable
                                       String bodyArgumentName,
                                       List<MediaType> consumesMediaTypes,
                                       List<MediaType> producesMediaTypes,
                                       boolean isPermitsBody,
                                       boolean isErrorRoute,
                                       MessageBodyHandlerRegistry messageBodyHandlerRegistry) {
        super(targetMethod, targetMethod.getReturnType(), consumesMediaTypes, producesMediaTypes, targetMethod.getDeclaringType(), isErrorRoute, isPermitsBody, messageBodyHandlerRegistry);
        this.targetMethod = targetMethod;

        Argument<?>[] arguments = targetMethod.getArguments();
         argumentNames = new String[arguments.length];
        if (arguments.length > 0) {
            Map<String, Argument<?>> requiredInputs = CollectionUtils.newLinkedHashMap(arguments.length);
            for (int i = 0; i < arguments.length; i++) {
                Argument<?> requiredArgument = arguments[i];
                String inputName = resolveInputName(requiredArgument);
                requiredInputs.put(inputName, requiredArgument);
                argumentNames[i] = inputName;
            }
            this.requiredInputs = Collections.unmodifiableMap(requiredInputs);
        } else {
            this.requiredInputs = Collections.emptyMap();
        }
        if (returnType.isVoid()) {
            isVoid = true;
        } else if (isSuspended()) {
            isVoid = KotlinExecutableMethodUtils.isKotlinFunctionReturnTypeUnit(targetMethod.getExecutableMethod());
        } else {
            isVoid = false;
        }
        if (bodyArgument != null) {
            optionalBodyArgument = Optional.of(bodyArgument);
        } else if (bodyArgumentName != null) {
            optionalBodyArgument = Optional.ofNullable(requiredInputs.get(bodyArgumentName));
        } else {
            optionalBodyArgument = Optional.empty();
        }
        optionalFullBodyArgument = super.getFullRequestBodyType();
        this.messageBodyReader = optionalBodyArgument.flatMap(b -> {
            if (b.isAsyncOrReactive() || b.isOptional()) {
                b = b.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            }
            return messageBodyHandlerRegistry.findReader(b, consumesMediaTypes);
        }).orElse(null);
        needsBody = optionalBodyArgument.isPresent() || hasArg(arguments, HttpRequest.class);
    }

    @Override
    public final MessageBodyReader<?> getMessageBodyReader() {
        return messageBodyReader;
    }

    private static boolean hasArg(Argument<?>[] arguments, Class<?> type) {
        for (Argument<?> argument : arguments) {
            if (argument.getType() == type) {
                return true;
            }
        }
        return false;
    }

    @Override
    public RequestArgumentBinder<Object>[] resolveArgumentBinders(RequestBinderRegistry requestBinderRegistry) {
        // Allow concurrent access
        if (argumentBinders == null) {
            argumentBinders = resolveArgumentBindersInternal(requestBinderRegistry);
        }
        return argumentBinders;
    }

    private RequestArgumentBinder<Object>[] resolveArgumentBindersInternal(RequestBinderRegistry requestBinderRegistry) {
        Argument<?>[] arguments = targetMethod.getArguments();
        if (arguments.length == 0) {
            return ZERO_BINDERS;
        }

        RequestArgumentBinder<Object>[] binders = new RequestArgumentBinder[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            Argument<?> argument = arguments[i];
            Optional<? extends ArgumentBinder<?, HttpRequest<?>>> argumentBinder = requestBinderRegistry.findArgumentBinder(argument);
            binders[i] = (RequestArgumentBinder<Object>) argumentBinder.orElse(null);
        }
        return binders;
    }

    @Override
    public boolean isVoid() {
        return isVoid;
    }

    /**
     * Resolves the name for an argument.
     *
     * @param argument the argument
     * @return the name
     */
    private static @NonNull String resolveInputName(@NonNull Argument<?> argument) {
        String inputName = argument.getAnnotationMetadata().stringValue(Bindable.NAME).orElse(null);
        if (StringUtils.isEmpty(inputName)) {
            inputName = argument.getName();
        }
        return inputName;
    }

    @Override
    public MethodExecutionHandle<T, R> getTargetMethod() {
        return targetMethod;
    }

    @Override
    public Optional<Argument<?>> getRequestBodyType() {
        return optionalBodyArgument;
    }

    @Override
    public Optional<Argument<?>> getFullRequestBodyType() {
        return optionalFullBodyArgument;
    }

    @Override
    public String[] getArgumentNames() {
        return argumentNames;
    }

    @Override
    public boolean needsRequestBody() {
        return needsBody || super.needsRequestBody();
    }
}
