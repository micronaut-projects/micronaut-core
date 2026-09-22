/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.chain.MethodInterceptorChain;
import io.micronaut.aop.runtime.RuntimeProxyDefinition;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The interceptor chains of the compiled methods of a Python bean's proxy. A compiled method of
 * the generated class runs its body as Java; when the bean is advised, the proxy the context hands
 * out binds this advice, and the method runs its interceptors in Java before the body, the way a
 * proxy compiled for a Java bean does. The chain ends on the target bean, whose own generated
 * class runs the body; the target binds no advice, so the body runs once.
 *
 * @param <T> The bean type
 * @since 5.3.0
 */
@Experimental
@Internal
@NullMarked
public final class StaticAdvice<T> {
    private final Map<String, List<RuntimeProxyDefinition.InterceptedMethod<T>>> interceptedByName = new LinkedHashMap<>();
    private final RuntimeProxyDefinition<T> proxyDefinition;
    private final Supplier<T> target;

    /**
     * @param proxyDefinition The proxy definition: the intercepted methods and their interceptors
     * @param target          The target bean of the proxy
     */
    public StaticAdvice(RuntimeProxyDefinition<T> proxyDefinition, Supplier<T> target) {
        this.proxyDefinition = proxyDefinition;
        this.target = target;
        for (RuntimeProxyDefinition.InterceptedMethod<T> interceptedMethod : proxyDefinition.interceptedMethods()) {
            interceptedByName.computeIfAbsent(interceptedMethod.executableMethod().getMethodName(), ignored -> new ArrayList<>())
                .add(interceptedMethod);
        }
    }

    /**
     * Runs a compiled method through its interceptors: the chain ends by calling the method on
     * the target bean, which runs the compiled body.
     *
     * @param methodName     The method
     * @param parameterTypes The names of the parameter types of the generated method, which pick its
     *                       executable method among overloads
     * @param arguments      The arguments of the call
     * @return The result of the chain
     */
    @SuppressWarnings("unchecked")
    public @Nullable Object proceed(String methodName, String[] parameterTypes, Object[] arguments) {
        T targetBean = target.get();
        RuntimeProxyDefinition.InterceptedMethod<T> intercepted = select(interceptedByName.get(methodName), parameterTypes);
        if (intercepted == null) {
            // the registry resolved no interceptor for the method: the body runs on the target as it would through the bridge
            ExecutableMethod<T, Object> executableMethod = executableMethod(methodName, parameterTypes);
            return executableMethod.invoke(targetBean, arguments);
        }
        ExecutableMethod<T, Object> executableMethod = intercepted.executableMethod();
        Interceptor<T, Object>[] interceptors = intercepted.interceptors();
        Interceptor<T, Object>[] chain = Arrays.copyOf(interceptors, interceptors.length + 1, Interceptor[].class);
        chain[interceptors.length] = context -> executableMethod.invoke(targetBean, context.getParameterValues());
        return new MethodInterceptorChain<>(chain, targetBean, executableMethod, arguments).proceed();
    }

    private ExecutableMethod<T, Object> executableMethod(String methodName, String[] parameterTypes) {
        List<ExecutableMethod<T, Object>> candidates = new ArrayList<>();
        for (ExecutableMethod<T, ?> executableMethod : proxyDefinition.proxyBeanDefinition().getExecutableMethods()) {
            if (executableMethod.getMethodName().equals(methodName)) {
                candidates.add((ExecutableMethod<T, Object>) executableMethod);
            }
        }
        ExecutableMethod<T, Object> match = selectBy(candidates, ExecutableMethod::getArguments, parameterTypes);
        if (match == null) {
            throw new IllegalStateException("No executable method [" + methodName + "] with parameters " + Arrays.toString(parameterTypes) + " on " + proxyDefinition.proxyBeanDefinition().getBeanType().getName());
        }
        return match;
    }

    private static <T> RuntimeProxyDefinition.@Nullable InterceptedMethod<T> select(@Nullable List<RuntimeProxyDefinition.InterceptedMethod<T>> candidates, String[] parameterTypes) {
        return candidates == null ? null : selectBy(candidates, method -> method.executableMethod().getArguments(), parameterTypes);
    }

    /**
     * The candidate declaring the parameter types of the generated method, the one whose body was
     * entered; when none spells them the same way, the first of the same arity.
     */
    private static <M> @Nullable M selectBy(List<M> candidates, Function<M, Argument<?>[]> argumentsOf, String[] parameterTypes) {
        M first = null;
        for (M candidate : candidates) {
            Argument<?>[] parameters = argumentsOf.apply(candidate);
            if (parameters.length != parameterTypes.length) {
                continue;
            }
            if (first == null) {
                first = candidate;
            }
            if (declares(parameters, parameterTypes)) {
                return candidate;
            }
        }
        return first;
    }

    private static boolean declares(Argument<?>[] parameters, String[] parameterTypes) {
        for (int i = 0; i < parameters.length; i++) {
            if (!typeName(parameters[i].getType()).equals(parameterTypes[i].replace('$', '.'))) {
                return false;
            }
        }
        return true;
    }

    private static String typeName(Class<?> type) {
        return type.getTypeName().replace('$', '.');
    }
}
