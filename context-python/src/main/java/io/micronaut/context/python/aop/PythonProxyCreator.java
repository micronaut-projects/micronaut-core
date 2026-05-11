/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context.python.aop;

import io.micronaut.aop.Adapter;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.chain.MethodInterceptorChain;
import io.micronaut.aop.runtime.RuntimeProxyCreator;
import io.micronaut.aop.runtime.RuntimeProxyDefinition;
import io.micronaut.context.python.ContextHolder;
import io.micronaut.context.python.GraalPyRuntimeUtil;
import io.micronaut.context.python.ValueCoercible;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static io.micronaut.aop.Adapter.InternalAttributes.ADAPTED_BEAN;

@Internal
@Singleton
@NullMarked
public final class PythonProxyCreator implements RuntimeProxyCreator {

    @Override
    public <T> T createProxy(RuntimeProxyDefinition<T> proxyDefinition) {
        @Nullable T targetBean;
        Value value;
        if (proxyDefinition.introduction()) {
            Class<?> pythonBeanType = resolvePythonBeanType(proxyDefinition);
            value = ContextHolder.findClass(pythonBeanType.getPackageName(), pythonBeanType.getSimpleName());
            targetBean = null;
        } else {
            targetBean = proxyDefinition.targetBean();
            value = ((ValueCoercible) targetBean).$unbox();
        }
        AtomicReference<Object> targetBeanRef = new AtomicReference<>();
        boolean isIntroduction = proxyDefinition.introduction();
        Map<String, List<RuntimeProxyDefinition.InterceptedMethod<T>>> interceptedMethodsByName = new LinkedHashMap<>();
        for (RuntimeProxyDefinition.InterceptedMethod<T> interceptedMethod : proxyDefinition.interceptedMethods()) {
            interceptedMethodsByName.computeIfAbsent(
                interceptedMethod.executableMethod().getMethodName(),
                ignored -> new ArrayList<>()
            ).add(interceptedMethod);
        }
        Map<String, ProxyExecutable> introductionFunctions = new LinkedHashMap<>();
        for (Map.Entry<String, List<RuntimeProxyDefinition.InterceptedMethod<T>>> entry : interceptedMethodsByName.entrySet()) {
            String methodName = entry.getKey();
            List<RuntimeProxyDefinition.InterceptedMethod<T>> interceptedMethods = entry.getValue();
            Value originalFunction = isIntroduction ? GraalPyRuntimeUtil.getRawClassMember(value, methodName) : value.getMember(methodName);
            ProxyExecutable proxiedFunction = createProxiedFunction(
                isIntroduction,
                value,
                targetBean,
                targetBeanRef,
                methodName,
                interceptedMethods,
                originalFunction
            );
            if (isIntroduction) {
                introductionFunctions.put(methodName, proxiedFunction);
            } else {
                value.putMember(methodName, proxiedFunction);
            }
        }
        if (isIntroduction) {
            fillAllAbstractMethods(value);
            Class<T> type = proxyDefinition.proxyBeanDefinition().getBeanType();
            Value targetValue = value.newInstance();
            T target = box(type, targetValue);
            if (target == null) {
                throw new IllegalStateException("Introduction proxy target cannot be null");
            }
            targetBeanRef.set(target);
            for (Map.Entry<String, ProxyExecutable> entry : introductionFunctions.entrySet()) {
                targetValue.putMember(entry.getKey(), entry.getValue());
            }
            return target;
        }
        if (targetBean == null) {
            throw new IllegalStateException("Target bean is null for non-introduction proxy");
        }
        return targetBean;
    }

    private Class<?> resolvePythonBeanType(RuntimeProxyDefinition<?> proxyDefinition) {
        for (RuntimeProxyDefinition.InterceptedMethod<?> interceptedMethod : proxyDefinition.interceptedMethods()) {
            Optional<Class> adaptedBean = interceptedMethod.executableMethod().classValue(Adapter.class, ADAPTED_BEAN);
            if (adaptedBean.isPresent()) {
                return adaptedBean.get();
            }
        }
        return proxyDefinition.proxyBeanDefinition().getBeanType();
    }

    private <T> ProxyExecutable createProxiedFunction(
        boolean isIntroduction,
        Value owner,
        @Nullable T targetBean,
        AtomicReference<Object> targetBeanRef,
        String methodName,
        List<RuntimeProxyDefinition.InterceptedMethod<T>> interceptedMethods,
        @Nullable Value originalFunction
    ) {
        return args -> {
            RuntimeProxyDefinition.InterceptedMethod<T> interceptedMethod = findInterceptedMethod(methodName, interceptedMethods, args);
            ExecutableMethod<T, ?> executableMethod = interceptedMethod.executableMethod();
            Interceptor<T, ?>[] interceptors = interceptedMethod.interceptors();

            Object[] javaArgs = fromPolyglotArray(args, executableMethod.getArguments());
            @SuppressWarnings("unchecked")
            T tb = targetBean != null ? targetBean : (T) targetBeanRef.get();
            if (tb == null) {
                throw new IllegalStateException("Target bean has not been initialized yet");
            }
            Interceptor<T, ?>[] finalInterceptors;
            if (isIntroduction && executableMethod.isAbstract()) {
                finalInterceptors = interceptors;
            } else {
                if (originalFunction == null) {
                    throw new IllegalStateException("No original function found for method: " + executableMethod);
                }
                finalInterceptors = Arrays.copyOf(interceptors, interceptors.length + 1, Interceptor[].class);
                finalInterceptors[finalInterceptors.length - 1] = invocationContext -> {
                    Value executable = isIntroduction
                        ? GraalPyRuntimeUtil.bindPythonDescriptor(originalFunction, tb, owner)
                        : originalFunction;
                    return executable.execute(
                        toPolyglotArray(invocationContext.getParameterValues(), executable.getContext())
                    );
                };
            }
            Object result = new MethodInterceptorChain(finalInterceptors, tb, executableMethod, javaArgs).proceed();
            return unbox(result);
        };
    }

    private <T> RuntimeProxyDefinition.InterceptedMethod<T> findInterceptedMethod(
        String methodName,
        List<RuntimeProxyDefinition.InterceptedMethod<T>> methods,
        Value[] args
    ) {
        for (RuntimeProxyDefinition.InterceptedMethod<T> method : methods) {
            if (method.executableMethod().getArguments().length == args.length) {
                return method;
            }
        }
        throw new IllegalArgumentException("No overload found for method " + methodName + " with " + args.length + " arguments");
    }

    @Nullable
    private Object unbox(@Nullable Object result) {
        return switch (result) {
            case null -> null;
            case Value value -> value;
            case ValueCoercible valueCoercible -> valueCoercible.asPolyglotValue();
            case List<?> list -> {
                List<Object> newList = new ArrayList<>(list.size());
                for (Object o : list) {
                    newList.add(unbox(o));
                }
                yield newList;
            }
            case Set<?> set -> {
                Set<Object> newSet = new LinkedHashSet<>(set.size());
                for (Object o : set) {
                    newSet.add(unbox(o));
                }
                yield newSet;
            }
            case Map<?, ?> map -> {
                Map<Object, Object> newMap = new java.util.HashMap<>(map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    newMap.put(unbox(entry.getKey()), unbox(entry.getValue()));
                }
                yield newMap;
            }
            case Object[] array -> {
                Object[] newArray = new Object[array.length];
                for (int i = 0; i < array.length; i++) {
                    newArray[i] = unbox(array[i]);
                }
                yield newArray;
            }
            case Iterable<?> iterable -> {
                List<Object> newList = new ArrayList<>();
                for (Object o : iterable) {
                    newList.add(unbox(o));
                }
                yield newList;
            }
            case Stream<?> stream -> stream.map(this::unbox);
            case Optional<?> optional -> optional.map(this::unbox);
            case CompletionStage<?> completionStage -> completionStage.thenApply(this::unbox);
            case Publisher<?> publisher -> Publishers.map(publisher, this::unbox);
            default -> result;
        };
    }

    private <T> @Nullable T box(Class<T> type, Value value) {
        if (value.isNull()) {
            return null;
        }
        if (type.isPrimitive()) {
            return value.as(type);
        }
        try {
            return type.getDeclaredConstructor(Value.class).newInstance(value);
        } catch (Exception e) {
            return value.as(type);
        }
    }

    @Nullable
    private Object box(Argument<?> argument, Value value) {
        if (value.isNull()) {
            return null;
        }
        Class<?> type = argument.getType();
        if (Iterable.class.isAssignableFrom(type)) {
            Argument<?> elementType = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            if (elementType == Argument.OBJECT_ARGUMENT) {
                return value.as(type);
            }
            List<Object> values = new ArrayList<>();
            if (value.hasArrayElements()) {
                long size = value.getArraySize();
                for (long i = 0; i < size; i++) {
                    values.add(box(elementType, value.getArrayElement(i)));
                }
            } else if (value.hasIterator()) {
                Value iterator = value.getIterator();
                while (iterator.hasIteratorNextElement()) {
                    values.add(box(elementType, iterator.getIteratorNextElement()));
                }
            } else {
                Iterable<?> iterable = value.as(Iterable.class);
                for (Object element : iterable) {
                    values.add(box(elementType, value.getContext().asValue(element)));
                }
            }
            if (Set.class.isAssignableFrom(type)) {
                return new LinkedHashSet<>(values);
            }
            return values;
        }
        if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            long size = value.getArraySize();
            Object array = Array.newInstance(componentType, (int) size);
            Argument<?> componentArgument = argument.getFirstTypeVariable().orElseGet(() -> Argument.of(componentType));
            for (int i = 0; i < size; i++) {
                Array.set(array, i, box(componentArgument, value.getArrayElement(i)));
            }
            return array;
        }
        return box(type, value);
    }

    private void fillAllAbstractMethods(Value pythonClass) {
        Context context = pythonClass.getContext();
        if (pythonClass.hasMember("__abstractmethods__")) {
            Value abstractMethodsValue = pythonClass.getMember("__abstractmethods__");
            if (abstractMethodsValue != null && abstractMethodsValue.hasIterator()) {
                Value iterator = abstractMethodsValue.getIterator();
                while (iterator.hasIteratorNextElement()) {
                    Value next = iterator.getIteratorNextElement();
                    if (next != null && next.isString()) {
                        String methodName = next.asString();
                        Value existingMember = pythonClass.getMember(methodName);
                        if (existingMember == null || !existingMember.canExecute()) {
                            pythonClass.putMember(methodName, (ProxyExecutable) args -> null);
                        }
                    }
                }
            }
            Value emptyAbstractMethods = context.eval("python", "frozenset()");
            pythonClass.putMember("__abstractmethods__", emptyAbstractMethods);
            Value isProtocol = pythonClass.getMember("_is_protocol");
            if (isProtocol != null && isProtocol.isBoolean() && isProtocol.asBoolean()) {
                pythonClass.putMember("_is_protocol", false);
            }
        }
    }

    private Object[] toPolyglotArray(Object[] in, Context context) {
        Object[] out = new Object[in.length];
        for (int i = 0; i < in.length; i++) {
            Object parameterValue = in[i];
            out[i] = context.asValue(parameterValue);
        }
        return out;
    }

    private Object[] fromPolyglotArray(Value[] in, Argument<?>[] arguments) {
        Object[] out = new Object[in.length];
        for (int i = 0; i < in.length; i++) {
            Value arg = in[i];
            Argument<?> argType = arguments[i];
            out[i] = box(argType, arg);
        }
        return out;
    }
}
