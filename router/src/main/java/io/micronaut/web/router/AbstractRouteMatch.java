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
import io.micronaut.http.MediaType;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.bind.binders.PostponedRequestArgumentBinder;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.http.bind.binders.UnmatchedRequestArgumentBinder;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.UnsafeExecutionHandle;
import io.micronaut.web.router.builder.DefaultPathVariables;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private static final @Nullable Object[] EMPTY_VALUES = new Object[0];
    /**
     * The value of an argument that is not fulfilled yet.
     */
    private static final Object UNFULFILLED = new Object();

    protected final ConversionService conversionService;
    protected final MethodBasedRouteInfo<T, R> routeInfo;
    protected final MethodExecutionHandle<T, R> methodExecutionHandle;
    protected final @Nullable UnsafeExecutionHandle<T, R> unsafeMethodExecutionHandle;
    protected final ExecutableMethod<T, R> executableMethod;

    private final Argument<?>[] arguments;
    private final String[] argumentNames;
    /**
     * The values of the arguments, {@link #UNFULFILLED} for an argument that is not fulfilled
     * yet; created when the binding starts, so a match that is never bound, e.g. a candidate that
     * is rejected, allocates nothing.
     */
    private @Nullable Object @Nullable [] argumentValues;
    /**
     * The results of the binders that wait, e.g. for the body; created when a binder waits.
     */
    private PendingRequestBindingResult<?> @Nullable [] pendingRequestBindingResults;
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
            // an empty array cannot be written to, so a route without arguments shares it
            this.argumentValues = EMPTY_VALUES;
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
        PendingRequestBindingResult<?>[] pendingRequestBindingResults = this.pendingRequestBindingResults;
        if (pendingRequestBindingResults == null) {
            // nothing waits, so nothing was fulfilled since the binding
            return false;
        }
        for (int i = 0; i < arguments.length; i++) {
            if (isArgumentFulfilled(i)) {
                continue;
            }
            PendingRequestBindingResult<?> pendingRequestBindingResult = pendingRequestBindingResults[i];
            if (pendingRequestBindingResult != null && !pendingRequestBindingResult.isPending()) {
                Argument<?> argument = arguments[i];
                setBindingResult(i, argument, pendingRequestBindingResult);
                failOnConversionErrors(argument, pendingRequestBindingResult);
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
                return isArgumentFulfilled(i);
            }
        }
        return false;
    }

    @Override
    public Method getTargetMethod() {
        return routeInfo.getTargetMethod().getTargetMethod();
    }

    @Override
    public boolean hasTargetMethod() {
        return routeInfo.getTargetMethod().hasTargetMethod();
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
            @Nullable Object[] argumentValues = argumentValues();
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
        @Nullable Object[] argumentValues = argumentValues();
        PendingRequestBindingResult<?>[] pendingRequestBindingResults = this.pendingRequestBindingResults;
        for (int i = 0; i < arguments.length; i++) {
            if (argumentValues[i] != UNFULFILLED) {
                continue;
            }
            PendingRequestBindingResult<?> pendingRequestBindingResult = pendingRequestBindingResults == null ? null : pendingRequestBindingResults[i];
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
            argumentValues[i] = null;
        }
        if (unsafeMethodExecutionHandle != null) {
            return unsafeMethodExecutionHandle.invokeUnsafe(argumentValues);
        }
        return methodExecutionHandle.invoke(argumentValues);
    }

    @Override
    public void fulfill(Map<String, Object> newValues) {
        if (fulfilled) {
            return;
        }
        @Nullable Object[] argumentValues = argumentValues();
        for (int i = 0; i < argumentNames.length; i++) {
            if (argumentValues[i] != UNFULFILLED) {
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

    /**
     * @return The values of the variables as they appear in the path, not percent-decoded
     */
    Map<String, Object> rawVariableValues() {
        return getVariableValues();
    }

    /**
     * @return The target a {@link DynamicRouteTarget} resolved for the route, or {@code null}
     */
    @Nullable Object resolvedTarget() {
        return null;
    }

    /**
     * @return The media type of the response a route selector negotiated, or {@code null}
     */
    @Nullable MediaType selectedMediaType() {
        return null;
    }

    @Override
    public void fulfillBeforeFilters(RequestBinderRegistry requestBinderRegistry, HttpRequest<?> request) {
        if (fulfilled) {
            return;
        }
        if (beforeBindersApplied) {
            throw new IllegalStateException("Argument before filters already processed!");
        }
        RequestArgumentBinder<Object>[] argumentBinders = routeInfo.resolveArgumentBinders(requestBinderRegistry);
        @Nullable Object[] argumentValues = argumentValues();
        for (int i = 0; i < arguments.length; i++) {
            if (argumentValues[i] != UNFULFILLED) {
                continue;
            }
            Argument<Object> argument = (Argument<Object>) arguments[i];
            if (arguments[i].getType() == PathVariables.class) {
                // a handler function's path variables come from the match, not a binder
                setValue(i, argument, new DefaultPathVariables(getVariableValues(), rawVariableValues(), conversionService, resolvedTarget(), selectedMediaType()));
                continue;
            }
            Object value = getVariableValues().get(argumentNames[i]);
            if (value != null) {
                setValue(i, argument, value);
                continue;
            }
            RequestArgumentBinder<Object> argumentBinder = argumentBinders[i];
            if (argumentBinder instanceof PostponedRequestArgumentBinder<Object>
                && !(argumentBinder instanceof UnmatchedRequestArgumentBinder)) {
                // bound after the filters, see fulfillAfterFilters
                // Allow for the unmatched request argument binder to run even so it's postponed
                continue;
            }
            if (argumentBinder != null) {
                fulfillValue(
                    i,
                    argumentBinder,
                    argument,
                    request
                );
            }
        }
        checkIfFulfilled();
        beforeBindersApplied = true;
    }

    @Override
    public void fulfillAfterFilters(RequestBinderRegistry requestBinderRegistry, HttpRequest<?> request) {
        if (fulfilled) {
            return;
        }
        if (afterBindersApplied) {
            throw new IllegalStateException("Argument binders after filters already processed!");
        }
        if (beforeBindersApplied) {
            // the route holds the binders, so an argument the binding before the filters left
            // unfulfilled, that has a postponed binder, is an argument the binder postponed
            RequestArgumentBinder<Object>[] argumentBinders = routeInfo.resolveArgumentBinders(requestBinderRegistry);
            @Nullable Object[] argumentValues = argumentValues();
            for (int i = 0; i < arguments.length; i++) {
                if (argumentValues[i] != UNFULFILLED) {
                    continue;
                }
                if (argumentBinders[i] instanceof PostponedRequestArgumentBinder<Object> argumentBinder) {
                    fulfillValuePostponed(
                        i,
                        argumentBinder,
                        (Argument<Object>) arguments[i],
                        request
                    );
                }
            }
            checkIfFulfilled();
        }
        afterBindersApplied = true;
    }

    private <E> void fulfillValue(int index,
                                  RequestArgumentBinder<E> argumentBinder,
                                  Argument<E> argument,
                                  HttpRequest<?> request) {
        ArgumentConversionContext<E> conversionContext = newContext(argument, request);
        ArgumentBinder.BindingResult<E> bindingResult = argumentBinder.bind(conversionContext, request);
        fulfillValue(index, argument, bindingResult);
    }

    private <E> void fulfillValuePostponed(int index,
                                           PostponedRequestArgumentBinder<E> argumentBinder,
                                           Argument<E> argument,
                                           HttpRequest<?> request) {
        ArgumentConversionContext<E> conversionContext = newContext(argument, request);
        ArgumentBinder.BindingResult<E> bindingResult = argumentBinder.bindPostponed(conversionContext, request);
        fulfillValue(index, argument, bindingResult);
    }

    private <E> ArgumentConversionContext<E> newContext(Argument<E> argument, HttpRequest<?> request) {
        return ConversionContext.of(
            argument,
            request.getLocale().orElse(null),
            request.getCharacterEncoding()
        );
    }

    private <E> void fulfillValue(int index, Argument<E> argument, ArgumentBinder.BindingResult<E> bindingResult) {
        if (bindingResult instanceof PendingRequestBindingResult<?> pendingRequestBindingResult) {
            PendingRequestBindingResult<?>[] pendingRequestBindingResults = this.pendingRequestBindingResults;
            if (pendingRequestBindingResults == null) {
                pendingRequestBindingResults = new PendingRequestBindingResult[arguments.length];
                this.pendingRequestBindingResults = pendingRequestBindingResults;
            }
            pendingRequestBindingResults[index] = pendingRequestBindingResult;
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
            argumentValues()[index] = value;
            return true;
        }
        setValue(index, argument, value);
        return true;
    }

    private void setValue(int index, Argument<?> argument, @Nullable Object value) {
        argumentValues()[index] = value == null ? null : convertValue(conversionService, argument, value);
    }

    private boolean isArgumentFulfilled(int index) {
        @Nullable Object[] argumentValues = this.argumentValues;
        return argumentValues != null && argumentValues[index] != UNFULFILLED;
    }

    /**
     * @return The values of the arguments, created when the binding starts
     */
    private @Nullable Object[] argumentValues() {
        @Nullable Object[] argumentValues = this.argumentValues;
        if (argumentValues == null) {
            argumentValues = new Object[arguments.length];
            Arrays.fill(argumentValues, UNFULFILLED);
            this.argumentValues = argumentValues;
        }
        return argumentValues;
    }

    private void checkIfFulfilled() {
        if (fulfilled) {
            return;
        }
        for (Object value : argumentValues()) {
            if (value == UNFULFILLED) {
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
