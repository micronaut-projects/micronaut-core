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

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.sse.Event;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Predicate;

/**
 * Abstract implementation of the {@link RouteMatch} interface.
 *
 * @param <T> The target type
 * @param <R> Route Match
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class AbstractRouteMatch<T, R> implements MethodBasedRouteMatch<T, R> {

    protected final MethodExecutionHandle<T, R> executableMethod;
    protected final ConversionService<?> conversionService;
    protected final Map<String, Argument> requiredInputs;
    protected final DefaultRouteBuilder.AbstractRoute abstractRoute;
    protected final List<MediaType> consumedMediaTypes;
    protected final List<MediaType> producedMediaTypes;

    /**
     * Constructor.
     *
     * @param abstractRoute     The abstract route builder
     * @param conversionService The conversion service
     */
    protected AbstractRouteMatch(DefaultRouteBuilder.AbstractRoute abstractRoute, ConversionService<?> conversionService) {
        this.abstractRoute = abstractRoute;
        //noinspection unchecked
        this.executableMethod = (MethodExecutionHandle<T, R>) abstractRoute.targetMethod;
        this.conversionService = conversionService;
        Argument[] requiredArguments = executableMethod.getArguments();
        this.requiredInputs = new LinkedHashMap<>(requiredArguments.length);
        for (Argument requiredArgument : requiredArguments) {
            String inputName = resolveInputName(requiredArgument);
            requiredInputs.put(inputName, requiredArgument);
        }

        this.consumedMediaTypes = abstractRoute.getConsumes();
        this.producedMediaTypes = abstractRoute.getProduces();
    }

    @Override
    public final boolean isSuspended() {
        return this.abstractRoute.isSuspended();
    }

    @Override
    public final boolean isReactive() {
        return this.abstractRoute.isReactive();
    }

    @Override
    public final boolean isSingleResult() {
        return this.abstractRoute.isSingleResult();
    }

    @Override
    public final boolean isSpecifiedSingle() {
        return this.abstractRoute.isSpecifiedSingle();
    }

    @Override
    public final boolean isAsync() {
        return this.abstractRoute.isAsync();
    }

    @Override
    public final boolean isVoid() {
        return this.abstractRoute.isVoid();
    }

    @Override
    public T getTarget() {
        return executableMethod.getTarget();
    }

    @NonNull
    @Override
    public ExecutableMethod<?, R> getExecutableMethod() {
        return executableMethod.getExecutableMethod();
    }

    @Override
    public List<MediaType> getProduces() {
        Optional<Argument<?>> firstTypeVariable = executableMethod.getReturnType().getFirstTypeVariable();
        if (firstTypeVariable.isPresent() && Event.class.isAssignableFrom(firstTypeVariable.get().getType())) {
            return Collections.singletonList(MediaType.TEXT_EVENT_STREAM_TYPE);
        } else {
            return abstractRoute.getProduces();
        }
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return executableMethod.getAnnotationMetadata();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<Argument<?>> getBodyArgument() {

        Argument<?> arg = abstractRoute.bodyArgument;
        if (arg != null) {
            return Optional.of(arg);
        }

        String bodyArgument = abstractRoute.bodyArgumentName;
        if (bodyArgument != null) {
            return Optional.ofNullable(requiredInputs.get(bodyArgument));
        }
        return Optional.empty();
    }

    @Override
    public boolean isRequiredInput(String name) {
        return requiredInputs.containsKey(name);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<Argument<?>> getRequiredInput(String name) {
        return Optional.ofNullable(requiredInputs.get(name));
    }

    @Override
    public boolean isExecutable() {
        Map<String, Object> variables = getVariableValues();
        for (Map.Entry<String, Argument> entry : requiredInputs.entrySet()) {
            Object value = variables.get(entry.getKey());
            if (value == null || value instanceof UnresolvedArgument) {
                return false;
            }
        }

        Optional<Argument<?>> bodyArgument = getBodyArgument();
        if (bodyArgument.isPresent()) {
            Object value = variables.get(bodyArgument.get().getName());
            return value != null && !(value instanceof UnresolvedArgument);
        }
        return true;
    }

    @Override
    public Method getTargetMethod() {
        return executableMethod.getTargetMethod();
    }

    @Override
    public String getMethodName() {
        return this.executableMethod.getMethodName();
    }

    @Override
    public Class getDeclaringType() {
        return executableMethod.getDeclaringType();
    }

    @Override
    public Argument[] getArguments() {
        return executableMethod.getArguments();
    }

    @Override
    public boolean test(HttpRequest request) {
        for (Predicate<HttpRequest<?>> condition : abstractRoute.conditions) {
            if (!condition.test(request)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ReturnType<R> getReturnType() {
        return executableMethod.getReturnType();
    }

    @Override
    public R invoke(Object... arguments) {
        ConversionService<?> conversionService = this.conversionService;

        Argument[] targetArguments = getArguments();
        if (targetArguments.length == 0) {
            return executableMethod.invoke();
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
            return executableMethod.invoke(argumentList.toArray());
        }
    }

    @Override
    public R execute(Map<String, Object> argumentValues) {
        Argument[] targetArguments = getArguments();

        if (targetArguments.length == 0) {
            return executableMethod.invoke();
        } else {
            ConversionService<?> conversionService = this.conversionService;
            Map<String, Object> uriVariables = getVariableValues();
            List<Object> argumentList = new ArrayList<>(argumentValues.size());

            for (Map.Entry<String, Argument> entry : requiredInputs.entrySet()) {
                Argument argument = entry.getValue();
                String name = entry.getKey();
                Object value = DefaultRouteBuilder.NO_VALUE;
                if (uriVariables.containsKey(name)) {
                    value = uriVariables.get(name);
                } else if (argumentValues.containsKey(name)) {
                    value = argumentValues.get(name);
                }

                Class argumentType = argument.getType();
                if (value instanceof UnresolvedArgument) {
                    UnresolvedArgument<?> unresolved = (UnresolvedArgument<?>) value;
                    ArgumentBinder.BindingResult<?> bindingResult = unresolved.get();


                    if (bindingResult.isPresentAndSatisfied()) {
                        Object resolved = bindingResult.get();
                        if (resolved instanceof ConversionError) {
                            ConversionError conversionError = (ConversionError) resolved;
                            throw new ConversionErrorException(argument, conversionError);
                        } else {
                            convertValueAndAddToList(conversionService, argumentList, argument, resolved, argumentType);
                        }
                    } else {
                        if (argument.isNullable()) {
                            argumentList.add(null);
                        } else {

                            List<ConversionError> conversionErrors = bindingResult.getConversionErrors();
                            if (!conversionErrors.isEmpty()) {
                                // should support multiple errors
                                ConversionError conversionError = conversionErrors.iterator().next();
                                throw new ConversionErrorException(argument, conversionError);
                            } else {
                                throw UnsatisfiedRouteException.create(argument);
                            }
                        }

                    }
                } else if (value instanceof NullArgument) {
                    argumentList.add(null);
                } else if (value instanceof ConversionError) {
                    throw new ConversionErrorException(argument, (ConversionError) value);
                } else if (value == DefaultRouteBuilder.NO_VALUE) {
                    throw UnsatisfiedRouteException.create(argument);
                } else {
                    convertValueAndAddToList(conversionService, argumentList, argument, value, argumentType);
                }
            }

            return executableMethod.invoke(argumentList.toArray());
        }
    }

    private void convertValueAndAddToList(ConversionService conversionService, List argumentList, Argument argument, Object value, Class argumentType) {
        if (argumentType.isInstance(value)) {
            if (argument.isContainerType()) {
                if (argument.hasTypeVariables()) {
                    ConversionContext conversionContext = ConversionContext.of(argument);
                    Optional<?> result = conversionService.convert(value, argumentType, conversionContext);
                    argumentList.add(resolveValueOrError(argument, conversionContext, result));
                } else {
                    argumentList.add(value);
                }
            } else {
                argumentList.add(value);
            }
        } else {
            ConversionContext conversionContext = ConversionContext.of(argument);
            Optional<?> result = conversionService.convert(value, argumentType, conversionContext);
            argumentList.add(resolveValueOrError(argument, conversionContext, result));
        }
    }

    @Override
    public boolean doesConsume(MediaType contentType) {
        return consumedMediaTypes.isEmpty() || contentType == null || consumedMediaTypes.contains(MediaType.ALL_TYPE) || explicitlyConsumes(contentType);
    }

    @Override
    public boolean doesProduce(@Nullable Collection<MediaType> acceptableTypes) {
        return producedMediaTypes == null || producedMediaTypes.isEmpty() || anyMediaTypesMatch(producedMediaTypes, acceptableTypes);
    }

    @Override
    public boolean doesProduce(@Nullable MediaType acceptableType) {
        return producedMediaTypes == null || producedMediaTypes.isEmpty() || producedMediaTypes.contains(acceptableType);
    }

    private boolean anyMediaTypesMatch(List<MediaType> producedMediaTypes, Collection<MediaType> acceptableTypes) {
        if (CollectionUtils.isEmpty(acceptableTypes)) {
            return true;
        } else {
            if (producedMediaTypes.contains(MediaType.ALL_TYPE)) {
                return true;
            }
            for (MediaType acceptableType : acceptableTypes) {
                if (acceptableType.equals(MediaType.ALL_TYPE) || producedMediaTypes.contains(acceptableType)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean explicitlyConsumes(MediaType contentType) {
        return consumedMediaTypes.contains(contentType);
    }

    @Override
    public RouteMatch<R> fulfill(Map<String, Object> argumentValues) {
        if (CollectionUtils.isEmpty(argumentValues)) {
            return this;
        } else {
            Map<String, Object> oldVariables = getVariableValues();
            Map<String, Object> newVariables = new LinkedHashMap<>(oldVariables);
            final Argument<?> bodyArgument = getBodyArgument().orElse(null);
            Argument[] arguments = getArguments();
            Collection<Argument> requiredArguments = getRequiredArguments();
            boolean hasRequiredArguments = CollectionUtils.isNotEmpty(requiredArguments);
            for (Argument requiredArgument : arguments) {

                String argumentName = requiredArgument.getName();
                if (argumentValues.containsKey(argumentName)) {

                    Object value = argumentValues.get(argumentName);
                    if (bodyArgument != null && bodyArgument.getName().equals(argumentName)) {
                        requiredArgument = bodyArgument;
                    }

                    if (hasRequiredArguments) {
                        requiredArguments.remove(requiredArgument);
                    }

                    if (value != null) {
                        String name = resolveInputName(requiredArgument);
                        if (value instanceof UnresolvedArgument || value instanceof NullArgument) {
                            newVariables.put(name, value);
                        } else {
                            Class type = requiredArgument.getType();
                            if (type.isInstance(value)) {
                                newVariables.put(name, value);
                            } else {
                                ArgumentConversionContext conversionContext = ConversionContext.of(requiredArgument);
                                Optional converted = conversionService.convert(value, conversionContext);
                                Object result = converted.isPresent() ? converted.get() : conversionContext.getLastError().orElse(null);
                                if (result != null) {
                                    newVariables.put(name, result);
                                }
                            }
                        }
                    }
                }
            }
            return newFulfilled(newVariables, (List<Argument>) requiredArguments);
        }
    }

    /**
     * @param argument          The argument
     * @param conversionContext The conversion context
     * @param result            An optional result
     * @return The resolved value or an error
     */
    protected Object resolveValueOrError(Argument argument, ConversionContext conversionContext, Optional<?> result) {
        if (!result.isPresent()) {
            Optional<ConversionError> lastError = conversionContext.getLastError();
            if (!lastError.isPresent() && argument.isDeclaredNullable()) {
                return null;
            }
            throw lastError.map(conversionError ->
                (RuntimeException) new ConversionErrorException(argument, conversionError)).orElseGet(() -> UnsatisfiedRouteException.create(argument)
            );
        } else {
            return result.get();
        }
    }

    /**
     * @param newVariables      The new variables
     * @param requiredArguments The required arguments
     * @return A RouteMatch
     */
    protected abstract RouteMatch<R> newFulfilled(Map<String, Object> newVariables, List<Argument> requiredArguments);

    private String resolveInputName(Argument requiredArgument) {
        String inputName = requiredArgument.getAnnotationMetadata().stringValue(Bindable.NAME).orElse(null);
        if (StringUtils.isEmpty(inputName)) {
            inputName = requiredArgument.getName();
        }
        return inputName;
    }
}
