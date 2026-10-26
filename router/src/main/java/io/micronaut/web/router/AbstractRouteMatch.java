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
import io.micronaut.http.bind.binders.PostponedRequestArgumentBinder;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.inject.ExecutableMethod;
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
    private final RouteArgumentPlan<T, R> argumentPlan;
    /**
     * The values of the arguments, {@link #UNFULFILLED} for an argument that is not fulfilled
     * yet; created when the binding starts, so a match that is never bound, e.g. a candidate that
     * is rejected, allocates nothing.
     */
    private @Nullable Object @Nullable [] argumentValues;
    /**
     * Results waiting for the body or postponed binding, including unmatched errors that
     * filters may replace; created only when needed.
     */
    private ArgumentBinder.BindingResult<?> @Nullable [] pendingRequestBindingResults;
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
        this.argumentPlan = routeInfo instanceof DefaultMethodBasedRouteInfo<T, R> info
            ? info.getArgumentPlan() : new RouteArgumentPlan<>(routeInfo);
        int length = argumentPlan.arguments.length;
        if (length == 0) {
            this.argumentValues = EMPTY_VALUES;
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
        return argumentPlan.executableMethod;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return argumentPlan.executableMethod.getAnnotationMetadata();
    }

    @Override
    public Optional<Argument<?>> getRequiredInput(String name) {
        for (int i = 0; i < argumentPlan.argumentNames.length; i++) {
            String argumentName = argumentPlan.argumentNames[i];
            if (name.equals(argumentName)) {
                return Optional.of(argumentPlan.arguments[i]);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isFulfilled() {
        if (fulfilled) {
            return true;
        }
        ArgumentBinder.BindingResult<?>[] pendingRequestBindingResults = this.pendingRequestBindingResults;
        if (pendingRequestBindingResults == null) {
            // nothing waits, so nothing was fulfilled since the binding
            return false;
        }
        for (int i = 0; i < argumentPlan.arguments.length; i++) {
            if (isArgumentFulfilled(i)) {
                continue;
            }
            ArgumentBinder.BindingResult<?> pendingRequestBindingResult = pendingRequestBindingResults[i];
            if (pendingRequestBindingResult != null && !pendingRequestBindingResult.isPending()) {
                Argument<?> argument = argumentPlan.arguments[i];
                setBindingResult(i, argument, pendingRequestBindingResult);
            }
        }
        checkIfFulfilled();
        return fulfilled;
    }

    @Override
    public boolean isSatisfied(String name) {
        for (int i = 0; i < argumentPlan.argumentNames.length; i++) {
            String argumentName = argumentPlan.argumentNames[i];
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
        return argumentPlan.executableMethod.getMethodName();
    }

    @Override
    public Class<T> getDeclaringType() {
        return argumentPlan.executableMethod.getDeclaringType();
    }

    @Override
    public Argument<?>[] getArguments() {
        return argumentPlan.executableMethod.getArguments();
    }

    @Override
    public ReturnType<R> getReturnType() {
        return argumentPlan.executableMethod.getReturnType();
    }

    @Override
    @Nullable
    public R invoke(@Nullable Object... arguments) {
        Argument<?>[] targetArguments = getArguments();
        if (targetArguments.length == 0) {
            return argumentPlan.methodExecutionHandle.invoke();
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
                    argumentList.add(result.orElseThrow(() -> new IllegalArgumentException("Wrong argument types to method: " + argumentPlan.executableMethod)));
                } else if (valueIterator.hasNext()) {
                    Optional<?> result = conversionService.convert(valueIterator.next(), targetArgument.getType());
                    argumentList.add(result.orElseThrow(() -> new IllegalArgumentException("Wrong argument types to method: " + argumentPlan.executableMethod)));
                } else if (i < arguments.length) {
                    Optional<?> result = conversionService.convert(arguments[i++], targetArgument.getType());
                    argumentList.add(result.orElseThrow(() -> new IllegalArgumentException("Wrong argument types to method: " + argumentPlan.executableMethod)));
                } else {
                    throw new IllegalArgumentException("Wrong number of arguments to method: " + argumentPlan.executableMethod);
                }
            }
            return argumentPlan.methodExecutionHandle.invoke(argumentList.toArray());
        }
    }

    @Override
    @Nullable
    public R execute() {
        Argument<?>[] targetArguments = getArguments();
        if (targetArguments.length == 0) {
            if (argumentPlan.unsafeMethodExecutionHandle != null) {
                return argumentPlan.unsafeMethodExecutionHandle.invokeUnsafe();
            }
            return argumentPlan.methodExecutionHandle.invoke();
        }
        if (fulfilled) {
            @Nullable Object[] argumentValues = argumentValues();
            if (argumentPlan.unsafeMethodExecutionHandle != null) {
                return argumentPlan.unsafeMethodExecutionHandle.invokeUnsafe(argumentValues);
            }
            return argumentPlan.methodExecutionHandle.invoke(argumentValues);
        }
        if (!beforeBindersApplied) {
            throw new IllegalStateException("Argument binders before filters not processed!");
        }
        if (!afterBindersApplied) {
            throw new IllegalStateException("Argument binders after filters not processed!");
        }
        @Nullable Object[] argumentValues = argumentValues();
        ArgumentBinder.BindingResult<?>[] pendingRequestBindingResults = this.pendingRequestBindingResults;
        for (int i = 0; i < argumentPlan.arguments.length; i++) {
            if (argumentValues[i] != UNFULFILLED) {
                continue;
            }
            ArgumentBinder.BindingResult<?> pendingRequestBindingResult = pendingRequestBindingResults == null ? null : pendingRequestBindingResults[i];
            Argument<?> argument = argumentPlan.arguments[i];
            if (pendingRequestBindingResult != null) {
                setBindingResultOfFail(i, argument, pendingRequestBindingResult);
                continue;
            }
            Object value = getVariableValues().get(argumentPlan.argumentNames[i]);
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
        if (argumentPlan.unsafeMethodExecutionHandle != null) {
            return argumentPlan.unsafeMethodExecutionHandle.invokeUnsafe(argumentValues);
        }
        return argumentPlan.methodExecutionHandle.invoke(argumentValues);
    }

    @Override
    public void fulfill(Map<String, Object> newValues) {
        if (fulfilled) {
            return;
        }
        @Nullable Object[] argumentValues = argumentValues();
        for (int i = 0; i < argumentPlan.argumentNames.length; i++) {
            if (argumentValues[i] != UNFULFILLED) {
                continue;
            }
            String argumentName = argumentPlan.argumentNames[i];
            Object value = newValues.get(argumentName);
            if (value != null) {
                setValue(i, argumentPlan.arguments[i], value);
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
        RequestArgumentBinder<Object>[] argumentBinders = argumentPlan.binders(routeInfo, requestBinderRegistry).before;
        @Nullable Object[] argumentValues = argumentValues();
        for (int i = 0; i < argumentPlan.arguments.length; i++) {
            if (argumentValues[i] != UNFULFILLED) {
                continue;
            }
            Argument<Object> argument = (Argument<Object>) argumentPlan.arguments[i];
            if (argumentPlan.arguments[i].getType() == PathVariables.class) {
                // a handler function's path variables come from the match, not a binder
                setValue(i, argument, new DefaultPathVariables(getVariableValues(), rawVariableValues(), conversionService, resolvedTarget(), selectedMediaType()));
                continue;
            }
            Object value = getVariableValues().get(argumentPlan.argumentNames[i]);
            if (value != null) {
                setValue(i, argument, value);
                continue;
            }
            RequestArgumentBinder<Object> argumentBinder = argumentBinders[i];
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
            PostponedRequestArgumentBinder<Object>[] argumentBinders = argumentPlan.binders(routeInfo, requestBinderRegistry).after;
            @Nullable Object[] argumentValues = argumentValues();
            for (int i = 0; i < argumentPlan.arguments.length; i++) {
                if (argumentValues[i] != UNFULFILLED) {
                    continue;
                }
                PostponedRequestArgumentBinder<Object> argumentBinder = argumentBinders[i];
                if (argumentBinder != null) {
                    fulfillValuePostponed(
                        i,
                        argumentBinder,
                        (Argument<Object>) argumentPlan.arguments[i],
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
        if (bindingResult.isPending()) {
            rememberBindingResult(index, bindingResult);
            return;
        }
        // a conversion error fails the request where the value is bound: before the filters for the
        // binders that run before them, like the unmatched binder
        failOnConversionErrors(argument, bindingResult);
        setBindingResult(index, argument, bindingResult);
    }

    private void rememberBindingResult(int index, ArgumentBinder.BindingResult<?> bindingResult) {
        ArgumentBinder.BindingResult<?>[] pendingRequestBindingResults = this.pendingRequestBindingResults;
        if (pendingRequestBindingResults == null) {
            pendingRequestBindingResults = new ArgumentBinder.BindingResult[argumentPlan.arguments.length];
            this.pendingRequestBindingResults = pendingRequestBindingResults;
        }
        pendingRequestBindingResults[index] = bindingResult;
    }

    private void setBindingResultOfFail(int index, Argument<?> argument, ArgumentBinder.BindingResult<?> bindingResult) {
        boolean isSet = setBindingResult(index, argument, bindingResult);
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
        failOnConversionErrors(argument, bindingResult);
        if (!bindingResult.isSatisfied()) {
            return false;
        }
        Optional<?> bound = bindingResult.getValue();
        if (bound.isEmpty() && (argument.getType() == Optional.class || !argument.isNullable())) {
            return false;
        }
        Object value = bound.orElse(null);
        if (value != null && bindingResult.isConvertedToArgumentType() && argumentPlan.wrapperTypes[index].isInstance(value)) {
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
            argumentValues = new Object[argumentPlan.arguments.length];
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
