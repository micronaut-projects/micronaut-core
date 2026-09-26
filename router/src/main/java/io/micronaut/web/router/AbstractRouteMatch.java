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
package io.micronaut.web.router;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.bind.binders.PostponedRequestArgumentBinder;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.UnsafeExecutionHandle;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Abstract implementation of the {@link RouteMatch} interface.
 *
 * @param <T> The target type
 * @param <R> Route Match
 * @author Graeme Rocher
 * @author Denis Stepanov
 * @since 1.0
 */
abstract class AbstractRouteMatch<T, R> implements MethodBasedRouteMatch<T, R> {

    private static final Object[] NO_VALUES = new Object[0];
    private static final boolean[] NO_FLAGS = new boolean[0];

    protected final ConversionService conversionService;
    protected final MethodBasedRouteInfo<T, R> routeInfo;
    protected final MethodExecutionHandle<T, R> methodExecutionHandle;
    protected final @Nullable UnsafeExecutionHandle<T, R> unsafeMethodExecutionHandle;
    protected final ExecutableMethod<T, R> executableMethod;

    private final Argument<?>[] arguments;
    private final String[] argumentNames;
    private final @Nullable Object[] argumentValues;
    /**
     * Allocated when a binder returns a pending result.
     */
    private PendingRequestBindingResult<?> @Nullable [] pendingRequestBindingResults;
    private final boolean[] fulfilledArguments;
    /**
     * The shape the binders before the filters ran with, which the binders after the filters use.
     */
    private @Nullable RouteShape shape;
    private boolean fulfilled;
    private boolean beforeBindersApplied;
    private boolean afterBindersApplied;

    /**
     * Constructor.
     *
     * @param routeInfo         The route info
     * @param conversionService The conversion service
     */
    protected AbstractRouteMatch(MethodBasedRouteInfo<T, R> routeInfo, ConversionService conversionService) {
        this.routeInfo = routeInfo;
        this.conversionService = conversionService;
        this.methodExecutionHandle = routeInfo.getTargetMethod();
        this.executableMethod = methodExecutionHandle.getExecutableMethod();
        this.arguments = executableMethod.getArguments();
        this.argumentNames = routeInfo.getArgumentNames();
        int length = arguments.length;
        if (length == 0) {
            this.argumentValues = NO_VALUES;
            this.fulfilledArguments = NO_FLAGS;
        } else {
            this.argumentValues = new Object[length];
            this.fulfilledArguments = new boolean[length];
        }
        if (methodExecutionHandle instanceof UnsafeExecutionHandle<?, ?>) {
            unsafeMethodExecutionHandle = (UnsafeExecutionHandle<T, R>) methodExecutionHandle;
        } else {
            unsafeMethodExecutionHandle = null;
        }
        this.fulfilled = length == 0;
    }

    @Override
    public RouteInfo<R> getRouteInfo() {
        return routeInfo;
    }

    @Override
    public T getTarget() {
        return routeInfo.getTargetMethod().getTarget();
    }

    @Override
    public ExecutableMethod<T, R> getExecutableMethod() {
        return executableMethod;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return executableMethod.getAnnotationMetadata();
    }

    @Override
    public Optional<Argument<?>> getRequiredInput(String name) {
        for (int i = 0; i < argumentNames.length; i++) {
            String argumentName = argumentNames[i];
            if (name.equals(argumentName)) {
                return Optional.of(arguments[i]);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isFulfilled() {
        if (fulfilled) {
            return true;
        }
        PendingRequestBindingResult<?>[] pendingResults = pendingRequestBindingResults;
        if (pendingResults != null) {
            for (int i = 0; i < arguments.length; i++) {
                boolean isFulfilled = fulfilledArguments[i];
                if (isFulfilled) {
                    continue;
                }
                PendingRequestBindingResult<?> pendingRequestBindingResult = pendingResults[i];
                if (pendingRequestBindingResult != null && !pendingRequestBindingResult.isPending()) {
                    Argument<?> argument = arguments[i];
                    setBindingResult(i, argument, pendingRequestBindingResult);
                    failOnConversionErrors(argument, pendingRequestBindingResult);
                }
            }
        }
        checkIfFulfilled();
        return fulfilled;
    }

    @Override
    public boolean isSatisfied(String name) {
        for (int i = 0; i < argumentNames.length; i++) {
            String argumentName = argumentNames[i];
            if (name.equals(argumentName)) {
                return fulfilledArguments[i];
            }
        }
        return false;
    }

    @Override
    public Method getTargetMethod() {
        return routeInfo.getTargetMethod().getTargetMethod();
    }

    @Override
    public String getMethodName() {
        return executableMethod.getMethodName();
    }

    @Override
    public Class<T> getDeclaringType() {
        return executableMethod.getDeclaringType();
    }

    @Override
    public Argument<?>[] getArguments() {
        return executableMethod.getArguments();
    }

    @Override
    public ReturnType<R> getReturnType() {
        return executableMethod.getReturnType();
    }

    @Override
    @Nullable
    public R invoke(@Nullable Object... arguments) {
        Argument<?>[] targetArguments = getArguments();
        if (targetArguments.length == 0) {
            return methodExecutionHandle.invoke();
        } else {
            List<Object> argumentList = new ArrayList<>(arguments.length);
            Map<String, Object> variables = getVariableValues();
            Iterator<Object> valueIterator = variables.values().iterator();
            int i = 0;
            for (Argument<?> targetArgument : targetArguments) {
                String name = targetArgument.getName();
                Object value = variables.get(name);
                if (value != null) {
                    Optional<?> result = conversionService.convert(value, targetArgument.getType());
                    argumentList.add(result.orElseThrow(() -> new IllegalArgumentException("Wrong argument types to method: " + executableMethod)));
                } else if (valueIterator.hasNext()) {
                    Optional<?> result = conversionService.convert(valueIterator.next(), targetArgument.getType());
                    argumentList.add(result.orElseThrow(() -> new IllegalArgumentException("Wrong argument types to method: " + executableMethod)));
                } else if (i < arguments.length) {
                    Optional<?> result = conversionService.convert(arguments[i++], targetArgument.getType());
                    argumentList.add(result.orElseThrow(() -> new IllegalArgumentException("Wrong argument types to method: " + executableMethod)));
                } else {
                    throw new IllegalArgumentException("Wrong number of arguments to method: " + executableMethod);
                }
            }
            return methodExecutionHandle.invoke(argumentList.toArray());
        }
    }

    @Override
    @Nullable
    public R execute() {
        Argument<?>[] targetArguments = getArguments();
        if (targetArguments.length == 0) {
            if (unsafeMethodExecutionHandle != null) {
                return unsafeMethodExecutionHandle.invokeUnsafe();
            }
            return methodExecutionHandle.invoke();
        }
        if (fulfilled) {
            if (unsafeMethodExecutionHandle != null) {
                return unsafeMethodExecutionHandle.invokeUnsafe(argumentValues);
            }
            return methodExecutionHandle.invoke(argumentValues);
        }
        if (!beforeBindersApplied) {
            throw new IllegalStateException("Argument binders before filters not processed!");
        }
        if (!afterBindersApplied) {
            throw new IllegalStateException("Argument binders after filters not processed!");
        }
        PendingRequestBindingResult<?>[] pendingResults = pendingRequestBindingResults;
        for (int i = 0; i < arguments.length; i++) {
            if (fulfilledArguments[i]) {
                continue;
            }
            PendingRequestBindingResult<?> pendingRequestBindingResult = pendingResults == null ? null : pendingResults[i];
            Argument<?> argument = arguments[i];
            if (pendingRequestBindingResult != null) {
                setBindingResultOfFail(i, argument, pendingRequestBindingResult);
                continue;
            }
            Object value = getVariableValues().get(argumentNames[i]);
            if (value != null) {
                setValue(i, argument, value);
                continue;
            }
            if (argument.isOptional()) {
                setValue(i, argument, Optional.empty());
                continue;
            }
            if (!argument.isNullable()) {
                throw UnsatisfiedRouteException.create(argument);
            }
        }
        if (methodExecutionHandle instanceof UnsafeExecutionHandle) {
            UnsafeExecutionHandle<T, R> unsafeExecutionHandle = (UnsafeExecutionHandle<T, R>) methodExecutionHandle;
            return unsafeExecutionHandle.invokeUnsafe(argumentValues);
        }
        return methodExecutionHandle.invoke(argumentValues);
    }

    @Override
    public void fulfill(Map<String, Object> newValues) {
        if (fulfilled) {
            return;
        }
        for (int i = 0; i < argumentNames.length; i++) {
            if (fulfilledArguments[i]) {
                continue;
            }
            String argumentName = argumentNames[i];
            Object value = newValues.get(argumentName);
            if (value != null) {
                setValue(i, arguments[i], value);
            }
        }
        checkIfFulfilled();
    }

    @Override
    public void fulfillBeforeFilters(RequestBinderRegistry requestBinderRegistry, HttpRequest<?> request) {
        if (fulfilled) {
            return;
        }
        if (beforeBindersApplied) {
            throw new IllegalStateException("Argument before filters already processed!");
        }
        RouteShape shape = resolveShape(requestBinderRegistry);
        this.shape = shape;
        RequestArgumentBinder<Object>[] argumentBinders = shape.binders;
        RouteShape.Slot[] slots = shape.slots;
        boolean[] pathVariables = shape.pathVariables;
        Map<String, Object> variableValues = null;
        // the locale and the character encoding are the same for each argument, so they are resolved once
        Locale locale = null;
        Charset characterEncoding = null;
        boolean conversionResolved = false;
        for (int i = 0; i < arguments.length; i++) {
            if (fulfilledArguments[i]) {
                continue;
            }
            Argument<Object> argument = (Argument<Object>) arguments[i];
            // a path variable of the same name wins over the binder, the template decides which arguments can be one
            if (pathVariables == null || pathVariables[i]) {
                if (variableValues == null) {
                    variableValues = getVariableValues();
                }
                Object value = variableValues.get(argumentNames[i]);
                if (value != null) {
                    setValue(i, argument, value);
                    continue;
                }
            }
            RouteShape.Slot slot = slots[i];
            if (slot == RouteShape.Slot.EARLY || slot == RouteShape.Slot.POSTPONED_AND_EARLY) {
                if (!conversionResolved) {
                    locale = request.getLocale().orElse(null);
                    characterEncoding = request.getCharacterEncoding();
                    conversionResolved = true;
                }
                ArgumentConversionContext<Object> conversionContext = ConversionContext.of(argument, locale, characterEncoding);
                fulfillValue(i, argument, argumentBinders[i].bind(conversionContext, request));
            }
        }
        checkIfFulfilled();
        beforeBindersApplied = true;
    }

    private RouteShape resolveShape(RequestBinderRegistry requestBinderRegistry) {
        if (routeInfo instanceof DefaultMethodBasedRouteInfo<T, R> defaultRouteInfo) {
            return defaultRouteInfo.shape(requestBinderRegistry, this);
        }
        // not cached for a route info of another kind
        return new RouteShape(requestBinderRegistry, routeInfo.resolveArgumentBinders(requestBinderRegistry), argumentNames, getClass(), null);
    }

    /**
     * The names of the variables the values of {@link #getVariableValues()} can have, the same for
     * each match of the route, or {@code null} when they are not known.
     *
     * @return The names of the path variables
     */
    @Nullable Set<String> pathVariableNames() {
        return null;
    }

    @Override
    public void fulfillAfterFilters(RequestBinderRegistry requestBinderRegistry, HttpRequest<?> request) {
        if (fulfilled) {
            return;
        }
        if (afterBindersApplied) {
            throw new IllegalStateException("Argument binders after filters already processed!");
        }
        // the postponed binders run for the arguments the binders before the filters did not fulfill
        RouteShape shape = this.shape;
        if (shape != null && shape.hasPostponed) {
            RequestArgumentBinder<Object>[] argumentBinders = shape.binders;
            RouteShape.Slot[] slots = shape.slots;
            Locale locale = null;
            Charset characterEncoding = null;
            boolean conversionResolved = false;
            for (int i = 0; i < arguments.length; i++) {
                if (fulfilledArguments[i]) {
                    continue;
                }
                RouteShape.Slot slot = slots[i];
                if (slot == RouteShape.Slot.POSTPONED || slot == RouteShape.Slot.POSTPONED_AND_EARLY) {
                    Argument<Object> argument = (Argument<Object>) arguments[i];
                    if (!conversionResolved) {
                        locale = request.getLocale().orElse(null);
                        characterEncoding = request.getCharacterEncoding();
                        conversionResolved = true;
                    }
                    ArgumentConversionContext<Object> conversionContext = ConversionContext.of(argument, locale, characterEncoding);
                    PostponedRequestArgumentBinder<Object> argumentBinder = (PostponedRequestArgumentBinder<Object>) argumentBinders[i];
                    fulfillValue(i, argument, argumentBinder.bindPostponed(conversionContext, request));
                }
            }
        }
        checkIfFulfilled();
        afterBindersApplied = true;
    }

    private <E> void fulfillValue(int index, Argument<E> argument, ArgumentBinder.BindingResult<E> bindingResult) {
        if (bindingResult instanceof PendingRequestBindingResult<?> pendingRequestBindingResult) {
            PendingRequestBindingResult<?>[] pendingResults = pendingRequestBindingResults;
            if (pendingResults == null) {
                pendingResults = new PendingRequestBindingResult[arguments.length];
                pendingRequestBindingResults = pendingResults;
            }
            pendingResults[index] = pendingRequestBindingResult;
            return;
        }
        failOnConversionErrors(argument, bindingResult);
        setBindingResult(index, argument, bindingResult);
    }

    private void setBindingResultOfFail(int index, Argument<?> argument, ArgumentBinder.BindingResult<?> bindingResult) {
        boolean isSet = setBindingResult(index, argument, bindingResult);
        failOnConversionErrors(argument, bindingResult);
        if (isSet) {
            return;
        }
        if (argument.isNullable()) {
            setValue(index, argument, null);
            return;
        }
        if (argument.isOptional()) {
            setValue(index, argument, Optional.empty());
            return;
        }
        throw UnsatisfiedRouteException.create(argument);
    }

    private void failOnConversionErrors(Argument<?> argument, ArgumentBinder.BindingResult<?> bindingResult) {
        List<ConversionError> conversionErrors = bindingResult.getConversionErrors();
        if (!conversionErrors.isEmpty()) {
            // should support multiple errors
            ConversionError conversionError = conversionErrors.iterator().next();
            throw new ConversionErrorException(argument, conversionError);
        }
    }

    private boolean setBindingResult(int index, Argument<?> argument, ArgumentBinder.BindingResult<?> bindingResult) {
        if (!bindingResult.isSatisfied()) {
            return false;
        }
        Object value;
        if (argument.getType() == Optional.class) {
            Optional<?> optionalValue = bindingResult.getValue();
            if (optionalValue.isPresent()) {
                value = optionalValue.get();
            } else {
                return false;
            }
        } else if (bindingResult.isPresentAndSatisfied()) {
            value = bindingResult.get();
        } else if (argument.isNullable() && bindingResult.isSatisfied()) {
            value = null;
        } else {
            return false;
        }
        if (value != null && bindingResult.isConvertedToArgumentType() && argument.getType().isInstance(value)) {
            // The binder already produced the value for the complete argument, including its type arguments
            argumentValues[index] = value;
            fulfilledArguments[index] = true;
            return true;
        }
        setValue(index, argument, value);
        return true;
    }

    private void setValue(int index, Argument<?> argument, @Nullable Object value) {
        if (value != null) {
            argumentValues[index] = convertValue(conversionService, argument, value);
        }
        fulfilledArguments[index] = true;
    }

    private void checkIfFulfilled() {
        if (fulfilled) {
            return;
        }
        for (boolean isFulfilled : fulfilledArguments) {
            if (!isFulfilled) {
                return;
            }
        }
        fulfilled = true;
    }

    private @Nullable Object convertValue(ConversionService conversionService, Argument<?> argument, Object value) {
        if (value instanceof ConversionError conversionError) {
            throw new ConversionErrorException(argument, conversionError);
        }
        Class<?> argumentType = argument.getType();
        if (argumentType.isInstance(value)) {
            if (argument.isContainerType()) {
                if (argument.hasTypeVariables()) {
                    ConversionContext conversionContext = ConversionContext.of(argument);
                    Optional<?> result = conversionService.convert(value, argumentType, conversionContext);
                    return resolveValueOrError(argument, conversionContext, result);
                }
            }
            return value;
        } else {
            ConversionContext conversionContext = ConversionContext.of(argument);
            Optional<?> result = conversionService.convert(value, argumentType, conversionContext);
            return resolveValueOrError(argument, conversionContext, result);
        }
    }

    private @Nullable Object resolveValueOrError(Argument<?> argument, ConversionContext conversionContext, Optional<?> result) {
        if (result.isEmpty()) {
            Optional<ConversionError> lastError = conversionContext.getLastError();
            if (lastError.isEmpty() && argument.isDeclaredNullable()) {
                return null;
            }
            throw lastError.map(conversionError ->
                (RuntimeException) new ConversionErrorException(argument, conversionError)).orElseGet(() -> UnsatisfiedRouteException.create(argument)
            );
        }
        return result.get();
    }

}
