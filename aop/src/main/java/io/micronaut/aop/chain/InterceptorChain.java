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
package io.micronaut.aop.chain;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.aop.*;
import io.micronaut.aop.exceptions.UnimplementedAdviceException;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.EnvironmentConfigurable;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.ExecutableMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * An internal representation of the {@link Interceptor} chain. This class implements {@link InvocationContext} and is
 * consumed by the framework itself and should not be used directly in application code.
 *
 * @param <B> The declaring type
 * @param <R> The result of the method call
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class InterceptorChain<B, R> implements InvocationContext<B, R> {
    protected static final Logger LOG = LoggerFactory.getLogger(InterceptorChain.class);

    protected final Interceptor<B, R>[] interceptors;
    protected final B target;
    protected final ExecutableMethod<B, R> executionHandle;
    protected final Object[] originalParameters;
    protected MutableConvertibleValues<Object> attributes;
    protected Map<String, MutableArgumentValue<?>> parameters;
    protected final int interceptorCount;
    protected int index = 0;

    /**
     * Constructor.
     *
     * @param interceptors array of interceptors
     * @param target target type
     * @param method result method
     * @param originalParameters parameters
     */
    public InterceptorChain(Interceptor<B, R>[] interceptors,
                            B target,
                            ExecutableMethod<B, R> method,
                            Object... originalParameters) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Intercepted method [{}] invocation on target: {}", method, target);
        }
        this.target = target;
        this.originalParameters = originalParameters;
        this.executionHandle = method;
        this.interceptors = interceptors;
        this.interceptorCount = interceptors.length;
    }

    @Override
    public Object[] getParameterValues() {
        return originalParameters;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return executionHandle.getAnnotationMetadata();
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        MutableConvertibleValues<Object> attributes = this.attributes;
        if (attributes == null) {
            synchronized (this) { // double check
                attributes = this.attributes;
                if (attributes == null) {
                    attributes = MutableConvertibleValues.of(new ConcurrentHashMap<>(5));
                    this.attributes = attributes;
                }
            }
        }
        return attributes;
    }

    @Override
    public Argument[] getArguments() {
        return executionHandle.getArguments();
    }

    @Override
    public Map<String, MutableArgumentValue<?>> getParameters() {
        Map<String, MutableArgumentValue<?>>  parameters = this.parameters;
        if (parameters == null) {
            synchronized (this) { // double check
                parameters = this.parameters;
                if (parameters == null) {
                    Argument[] arguments = executionHandle.getArguments();
                    parameters = new LinkedHashMap<>(arguments.length);
                    for (int i = 0; i < arguments.length; i++) {
                        Argument argument = executionHandle.getArguments()[i];
                        int finalIndex = i;
                        parameters.put(argument.getName(), new MutableArgumentValue<Object>() {
                            @Override
                            public AnnotationMetadata getAnnotationMetadata() {
                                return argument.getAnnotationMetadata();
                            }

                            @Override
                            public Optional<Argument<?>> getFirstTypeVariable() {
                                return argument.getFirstTypeVariable();
                            }

                            @Override
                            public Argument[] getTypeParameters() {
                                return argument.getTypeParameters();
                            }

                            @Override
                            public Map<String, Argument<?>> getTypeVariables() {
                                return argument.getTypeVariables();
                            }

                            @NonNull
                            @Override
                            public String getName() {
                                return argument.getName();
                            }

                            @NonNull
                            @Override
                            public Class<Object> getType() {
                                return argument.getType();
                            }

                            @Override
                            public boolean equalsType(Argument<?> other) {
                                return argument.equalsType(other);
                            }

                            @Override
                            public int typeHashCode() {
                                return argument.typeHashCode();
                            }

                            @Override
                            public Object getValue() {
                                return originalParameters[finalIndex];
                            }

                            @Override
                            public void setValue(Object value) {
                                originalParameters[finalIndex] = value;
                            }
                        });
                    }
                    parameters = Collections.unmodifiableMap(parameters);
                    this.parameters = parameters;
                }
            }
        }
        return parameters;
    }

    @Override
    public R invoke(B instance, Object... arguments) {
        return proceed();
    }

    @Override
    public B getTarget() {
        return target;
    }

    @Override
    public R proceed() throws RuntimeException {
        Interceptor<B, R> interceptor;
        if (interceptorCount == 0 || index == interceptorCount) {
            try {
                return executionHandle.invoke(target, getParameterValues());
            } catch (AbstractMethodError e) {
                throw new UnimplementedAdviceException(executionHandle);
            }
        } else {
            interceptor = this.interceptors[index++];
            if (LOG.isTraceEnabled()) {
                LOG.trace("Proceeded to next interceptor [{}] in chain for method invocation: {}", interceptor, executionHandle);
            }

            return interceptor.intercept(this);
        }
    }

    @Override
    public R proceed(Interceptor from) throws RuntimeException {
        for (int i = 0; i < interceptors.length; i++) {
            Interceptor<B, R> interceptor = interceptors[i];
            if (interceptor == from) {
                index = i + 1;
                return proceed();

            }
        }
        throw new IllegalArgumentException("Argument [" + from + "] is not within the interceptor chain");
    }

    /**
     * Resolves the {@link Around} interceptors for a method.
     *
     * @param beanContext bean context passed in
     * @param method The method
     * @param interceptors The array of interceptors
     * @return The filtered array of interceptors
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    public static Interceptor[] resolveAroundInterceptors(BeanContext beanContext, ExecutableMethod<?, ?> method, Interceptor... interceptors) {
        instrumentAnnotationMetadata(beanContext, method);
        return resolveInterceptorsInternal(method, Around.class, interceptors, beanContext != null ? beanContext.getClassLoader() : null);
    }

    /**
     * Resolves the interceptors for a method for {@link Introduction} advise. For {@link Introduction} advise
     * any {@link Around} advise interceptors are applied first
     *
     * @param beanContext Bean Context
     * @param method The method
     * @param interceptors The array of interceptors
     * @return The filtered array of interceptors
     */
    @Internal
    @UsedByGeneratedCode
    public static Interceptor[] resolveIntroductionInterceptors(@Nullable BeanContext beanContext,
                                                                ExecutableMethod<?, ?> method,
                                                                Interceptor... interceptors) {
        instrumentAnnotationMetadata(beanContext, method);
        Interceptor[] introductionInterceptors = resolveInterceptorsInternal(method, Introduction.class, interceptors, beanContext != null ? beanContext.getClassLoader() : null);
        if (introductionInterceptors.length == 0) {
            if (method.hasStereotype(Adapter.class)) {
                introductionInterceptors = new Interceptor[] { new AdapterIntroduction(beanContext, method) };
            } else {
                throw new IllegalStateException("At least one @Introduction method interceptor required, but missing. Check if your @Introduction stereotype annotation is marked with @Retention(RUNTIME) and @Type(..) with the interceptor type. Otherwise do not load @Introduction beans if their interceptor definitions are missing!");

            }
        }
        Interceptor[] aroundInterceptors = resolveAroundInterceptors(beanContext, method, interceptors);
        return ArrayUtils.concat(aroundInterceptors, introductionInterceptors);
    }

    private static void instrumentAnnotationMetadata(BeanContext beanContext, ExecutableMethod<?, ?> method) {
        if (beanContext instanceof ApplicationContext && method instanceof EnvironmentConfigurable) {
            // ensure metadata is environment aware
            ((EnvironmentConfigurable) method).configure(((ApplicationContext) beanContext).getEnvironment());
        }
    }

    private static Interceptor[] resolveInterceptorsInternal(ExecutableMethod<?, ?> method, Class<? extends Annotation> annotationType, Interceptor[] interceptors, @Nullable ClassLoader classLoader) {
        List<Class<? extends Annotation>> annotations = method.getAnnotationTypesByStereotype(annotationType, classLoader);

        Set<Class> applicableClasses = new HashSet<>();

        for (Class<? extends Annotation> aClass: annotations) {
            if (annotationType == Around.class && aClass.getAnnotation(Around.class) == null && aClass.getAnnotation(Introduction.class) != null) {
                continue;
            } else if (annotationType == Introduction.class && aClass.getAnnotation(Introduction.class) == null && aClass.getAnnotation(Around.class) != null) {
                continue;
            }
            Type typeAnn = aClass.getAnnotation(Type.class);
            if (typeAnn != null) {
                applicableClasses.addAll(Arrays.asList(typeAnn.value()));
            }
        }

        Interceptor[] interceptorArray = Arrays.stream(interceptors)
            .filter(i -> applicableClasses.stream().anyMatch(t -> t.isInstance(i)))
            .toArray(Interceptor[]::new);
        OrderUtil.sort(interceptorArray);
        return interceptorArray;
    }
}
