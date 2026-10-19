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

import io.micronaut.aop.Adapter;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.chain.MethodInterceptorChain;
import io.micronaut.aop.runtime.RuntimeProxyDefinition;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
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
 * class runs the body; the target binds no advice, so the body runs once. The abstract methods
 * of an introduction run the introduction's interceptors alone, on the proxy itself.
 *
 * @param <T> The bean type
 * @since 5.3.0
 */
@Experimental
@Internal
public final class StaticAdvice<T> {
    private final Map<String, List<RuntimeProxyDefinition.InterceptedMethod<T>>> interceptedByName = new LinkedHashMap<>();
    private final RuntimeProxyDefinition<T> proxyDefinition;
    private final Supplier<T> target;
    private final boolean introduction;

    /**
     * @param proxyDefinition The proxy definition: the intercepted methods and their interceptors
     * @param target          The target bean of the proxy
     */
    public StaticAdvice(RuntimeProxyDefinition<T> proxyDefinition, Supplier<T> target) {
        this(proxyDefinition, proxyDefinition.interceptedMethods(), target, false);
    }

    /**
     * @param proxyDefinition    The proxy definition
     * @param interceptedMethods The intercepted methods and their interceptors, as the proxy runs them
     * @param target             The target bean of the proxy
     * @param introduction       Whether the target is the introduction proxy itself, which only its
     *                           introduction interceptors answer
     */
    private StaticAdvice(RuntimeProxyDefinition<T> proxyDefinition, List<RuntimeProxyDefinition.InterceptedMethod<T>> interceptedMethods, Supplier<T> target, boolean introduction) {
        this.proxyDefinition = proxyDefinition;
        this.target = target;
        this.introduction = introduction;
        for (RuntimeProxyDefinition.InterceptedMethod<T> interceptedMethod : interceptedMethods) {
            interceptedByName.computeIfAbsent(interceptedMethod.executableMethod().getMethodName(), ignored -> new ArrayList<>())
                .add(interceptedMethod);
        }
    }

    /**
     * The advice of an introduction proxy: the target is the proxy itself, so only an abstract
     * method its introduction interceptors answer runs through the advice.
     *
     * @param proxyDefinition    The proxy definition
     * @param interceptedMethods The intercepted methods and their interceptors, as the proxy runs them
     * @param proxy              The introduction proxy
     * @param <T>                The bean type
     * @return The advice
     */
    public static <T> StaticAdvice<T> introduction(RuntimeProxyDefinition<T> proxyDefinition, List<RuntimeProxyDefinition.InterceptedMethod<T>> interceptedMethods, Supplier<T> proxy) {
        return new StaticAdvice<>(proxyDefinition, interceptedMethods, proxy, true);
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
            if (introduction) {
                throw selfCall(methodName, "no introduction interceptor answers it");
            }
            // the registry resolved no interceptor for the method: the body runs on the target as it would through the bridge
            ExecutableMethod<T, Object> executableMethod = executableMethod(methodName, parameterTypes);
            return executableMethod.invoke(targetBean, arguments);
        }
        ExecutableMethod<T, Object> executableMethod = intercepted.executableMethod();
        Interceptor<T, Object>[] interceptors = intercepted.interceptors();
        if (executableMethod.isAbstract()) {
            // an introduced method: the introduction answers the call, nothing runs after the interceptors
            if (executableMethod.hasStereotype(Adapter.class) && interceptors.length > 1) {
                // as the Python proxy: the adapter introduction alone, the around interceptors advise the target method
                interceptors = Arrays.copyOfRange(interceptors, interceptors.length - 1, interceptors.length);
            }
            return new MethodInterceptorChain<>(interceptors, targetBean, executableMethod, arguments).proceed();
        }
        if (introduction) {
            throw selfCall(methodName, "it is not abstract, so its chain would end on the proxy");
        }
        Interceptor<T, Object>[] chain = Arrays.copyOf(interceptors, interceptors.length + 1, Interceptor[].class);
        chain[interceptors.length] = context -> executableMethod.invoke(targetBean, context.getParameterValues());
        return new MethodInterceptorChain<>(chain, targetBean, executableMethod, arguments).proceed();
    }

    /**
     * The target of an introduction is the proxy itself: a call the introduction does not answer
     * would enter the proxy's own method again, until the stack overflows.
     */
    private IllegalStateException selfCall(String methodName, String why) {
        return new IllegalStateException("The method [" + methodName + "] of the introduction "
            + proxyDefinition.proxyBeanDefinition().getBeanType().getName()
            + " cannot run through its compiled advice: " + why + ", and the target of an introduction is the proxy itself");
    }

    /**
     * Converts what an interceptor chain returned to the return type of the generated method, as
     * the bridge converts the value of a Python call: a number to a primitive or its box only when
     * it fits exactly, {@code None} to no primitive.
     *
     * @param value      What the chain returned
     * @param returnType The name of the return type of the generated method
     * @return The value, of the return type when it is a primitive or its box
     * @throws NullPointerException When the value is {@code null} and the return type is a primitive
     * @throws ClassCastException   When the value does not convert to the return type
     */
    public static @Nullable Object result(@Nullable Object value, String returnType) {
        boolean primitive = switch (returnType) {
            case "boolean", "byte", "short", "int", "long", "float", "double", "char" -> true;
            default -> false;
        };
        if (value == null) {
            if (primitive) {
                throw new NullPointerException("Cannot convert null value to Java type '" + returnType + "'");
            }
            return null;
        }
        return switch (returnType) {
            case "boolean", "java.lang.Boolean" -> {
                if (value instanceof Boolean) {
                    yield value;
                }
                throw lossy(value, returnType);
            }
            case "byte", "java.lang.Byte" -> (byte) integral(value, Byte.MIN_VALUE, Byte.MAX_VALUE, returnType);
            case "short", "java.lang.Short" -> (short) integral(value, Short.MIN_VALUE, Short.MAX_VALUE, returnType);
            case "int", "java.lang.Integer" -> (int) integral(value, Integer.MIN_VALUE, Integer.MAX_VALUE, returnType);
            case "long", "java.lang.Long" -> integral(value, Long.MIN_VALUE, Long.MAX_VALUE, returnType);
            case "double", "java.lang.Double" -> floating(value, returnType);
            case "float", "java.lang.Float" -> {
                double d = floating(value, returnType);
                if ((float) d != d && !Double.isNaN(d)) {
                    throw lossy(value, returnType);
                }
                yield (float) d;
            }
            default -> value;
        };
    }

    private static long integral(Object value, long min, long max, String type) {
        long result;
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            result = ((Number) value).longValue();
        } else if (value instanceof java.math.BigInteger big && big.bitLength() < Long.SIZE) {
            result = big.longValue();
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            result = (long) d;
            if (result != d || (d == 0 && 1 / d < 0)) {
                throw lossy(value, type);
            }
        } else {
            throw lossy(value, type);
        }
        if (result < min || result > max) {
            throw lossy(value, type);
        }
        return result;
    }

    private static double floating(Object value, String type) {
        if (value instanceof Double || value instanceof Float) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            long l = ((Number) value).longValue();
            double d = l;
            if ((long) d != l) {
                throw lossy(value, type);
            }
            return d;
        }
        throw lossy(value, type);
    }

    private static ClassCastException lossy(Object value, String type) {
        return new ClassCastException("Cannot convert '" + value + "' (" + value.getClass().getName() + ") to Java type '" + type + "' without loss");
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
