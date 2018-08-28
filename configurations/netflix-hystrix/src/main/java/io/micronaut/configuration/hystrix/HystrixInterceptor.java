/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.configuration.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.configuration.hystrix.annotation.Hystrix;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.retry.intercept.RecoveryInterceptor;
import rx.Observable;
import rx.SingleSubscriber;

import javax.inject.Singleton;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * A {@link MethodInterceptor} that adds support for decorating methods for Hystrix.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class HystrixInterceptor implements MethodInterceptor<Object, Object> {

    /**
     * The attribute used the Hystrix command to be executed within the {@link MethodInvocationContext}.
     */
    public static final String ATTRIBUTE_COMMAND = "hystrix.command";

    /**
     * The attribute used the Hystrix command to be executed within the {@link MethodInvocationContext}.
     */
    public static final String ATTRIBUTE_GROUP = "hystrix.group";

    public static final int POSITION = InterceptPhase.RETRY.getPosition() + 10;

    private final Map<ExecutableMethod, HystrixCommand.Setter> setterMap = new ConcurrentHashMap<>();
    private final Map<ExecutableMethod, HystrixObservableCommand.Setter> observableSetterMap = new ConcurrentHashMap<>();

    private final RecoveryInterceptor recoveryInterceptor;

    /**
     * Constructor.
     * @param recoveryInterceptor recoveryInterceptor
     */
    public HystrixInterceptor(RecoveryInterceptor recoveryInterceptor) {
        this.recoveryInterceptor = recoveryInterceptor;
    }

    @Override
    public int getOrder() {
        return POSITION;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Class<io.micronaut.configuration.hystrix.annotation.HystrixCommand> annotationType = io.micronaut.configuration.hystrix.annotation.HystrixCommand.class;
        AnnotationValue<Hystrix> settings = context.getAnnotation(Hystrix.class);
        AnnotationValue<io.micronaut.configuration.hystrix.annotation.HystrixCommand> cmd = context.getAnnotation(annotationType);
        if (cmd == null) {
            return context.proceed();
        } else {

            String hystrixGroup = resolveHystrixGroup(context, settings);
            String commandName = cmd.getValue(String.class).orElse(context.getMethodName());
            String threadPool = settings != null ? settings.get("threadPool", String.class, null) : null;
            boolean wrapExceptions = settings != null ? settings.getRequiredValue("wrapExceptions", Boolean.class) : false;

            ReturnType<Object> returnType = context.getReturnType();
            Class<Object> javaReturnType = returnType.getType();
            context.setAttribute(ATTRIBUTE_GROUP, hystrixGroup);
            context.setAttribute(ATTRIBUTE_COMMAND, commandName);

            boolean isFuture = CompletableFuture.class.isAssignableFrom(javaReturnType);
            if (Publishers.isConvertibleToPublisher(javaReturnType) || isFuture) {
                HystrixObservableCommand.Setter setter = observableSetterMap.computeIfAbsent(context.getExecutableMethod(), method ->
                    buildObservableSetter(hystrixGroup, commandName, settings)
                );

                HystrixObservableCommand<Object> command = new HystrixObservableCommand<Object>(setter) {
                    @SuppressWarnings("unchecked")
                    @Override
                    protected Observable<Object> construct() {
                        Object result = context.proceed();
                        return ConversionService.SHARED.convert(result, Observable.class)
                            .orElseThrow(() -> new IllegalStateException("Unsupported Reactive type: " + javaReturnType));
                    }

                    @Override
                    protected boolean isFallbackUserDefined() {
                        return recoveryInterceptor.findFallbackMethod(context).isPresent();
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    protected Observable<Object> resumeWithFallback() {
                        Optional<MethodExecutionHandle<Object>> fallbackMethod = recoveryInterceptor.findFallbackMethod(context);
                        if (fallbackMethod.isPresent()) {
                            MethodExecutionHandle<Object> handle = fallbackMethod.get();
                            Object result = handle.invoke(context.getParameterValues());
                            Optional<Observable> converted = ConversionService.SHARED.convert(result, Observable.class);
                            if (converted.isPresent()) {
                                return converted.get();
                            }
                        }
                        return super.resumeWithFallback();
                    }

                    @Override
                    protected boolean shouldNotBeWrapped(Throwable underlying) {
                        return !wrapExceptions || super.shouldNotBeWrapped(underlying);
                    }
                };
                if (isFuture) {
                    CompletableFuture future = new CompletableFuture();
                    command.toObservable().toSingle().subscribe(new SingleSubscriber<Object>() {
                        @Override
                        public void onSuccess(Object value) {
                            //noinspection unchecked
                            future.complete(value);
                        }

                        @Override
                        public void onError(Throwable error) {
                            future.completeExceptionally(error);
                        }
                    });
                    return future;
                } else {
                    return ConversionService.SHARED.convert(command.toObservable(), returnType.asArgument())
                        .orElseThrow(() -> new IllegalStateException("Unsupported Reactive type: " + javaReturnType));
                }
            } else {
                String finalCommandName = commandName;
                HystrixCommand.Setter setter = setterMap.computeIfAbsent(context.getExecutableMethod(), method ->
                    buildSetter(hystrixGroup, finalCommandName, threadPool, settings)
                );

                HystrixCommand<Object> hystrixCommand = new HystrixCommand<Object>(setter) {
                    @Override
                    protected Object run() {
                        return context.proceed();
                    }

                    @Override
                    protected boolean isFallbackUserDefined() {
                        return recoveryInterceptor.findFallbackMethod(context).isPresent();
                    }

                    @Override
                    protected Object getFallback() {
                        Optional<MethodExecutionHandle<Object>> fallbackMethod = recoveryInterceptor.findFallbackMethod(context);
                        if (fallbackMethod.isPresent()) {
                            MethodExecutionHandle<Object> handle = fallbackMethod.get();
                            return handle.invoke(context.getParameterValues());
                        }
                        return super.getFallback();
                    }

                    @Override
                    protected boolean shouldNotBeWrapped(Throwable underlying) {
                        return !wrapExceptions || super.shouldNotBeWrapped(underlying);
                    }
                };
                try {
                    return hystrixCommand.execute();
                } catch (Exception e) {

                    if (!wrapExceptions) {
                        // unpack the original exception
                        //noinspection ConstantConditions
                        if (e instanceof ExecutionException) {
                            Throwable cause = e.getCause();
                            if (cause instanceof RuntimeException) {
                                throw (RuntimeException) cause;
                            }
                        }
                    }
                    throw e;
                }
            }
        }
    }

    @SuppressWarnings("Duplicates")
    private HystrixCommand.Setter buildSetter(String hystrixGroup, String commandName, String threadPool, AnnotationValue<Hystrix> settings) {
        HystrixCommand.Setter setter = HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(hystrixGroup));
        HystrixCommandKey commandKey = HystrixCommandKey.Factory.asKey(commandName);
        setter.andCommandKey(commandKey);
        if (StringUtils.isNotEmpty(threadPool)) {
            setter.andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey(threadPool));
        }
        if (settings != null) {
            List<AnnotationValue<Property>> properties = settings.getAnnotations("commandProperties", Property.class);
            if (!properties.isEmpty()) {

                HystrixCommandProperties.Setter instance = buildCommandProperties(properties);
                setter.andCommandPropertiesDefaults(
                    instance
                );
            }

            List<AnnotationValue<Property>> threadPoolProps = settings.getAnnotations("threadPoolProperties", Property.class);
            if (!threadPoolProps.isEmpty()) {

                HystrixThreadPoolProperties.Setter threadPoolPropsInstance = buildThreadPoolProperties(properties);
                setter.andThreadPoolPropertiesDefaults(
                    threadPoolPropsInstance
                );

            }
        }
        return setter;
    }

    private HystrixCommandProperties.Setter buildCommandProperties(List<AnnotationValue<Property>> properties) {
        Class<HystrixCommandProperties.Setter> setterClass = HystrixCommandProperties.Setter.class;
        HystrixCommandProperties.Setter instance = HystrixCommandProperties.defaultSetter();
        return buildPropertiesDynamic(properties, setterClass, instance);
    }

    private HystrixThreadPoolProperties.Setter buildThreadPoolProperties(List<AnnotationValue<Property>> properties) {
        return buildPropertiesDynamic(
            properties,
            HystrixThreadPoolProperties.Setter.class,
            HystrixThreadPoolProperties.defaultSetter());
    }

    private <T> T buildPropertiesDynamic(List<AnnotationValue<Property>> properties, Class<T> setterClass, T instance) {
        for (AnnotationValue<Property> property : properties) {
            String name = property.get("name", String.class).orElse(null);
            if (StringUtils.isNotEmpty(name)) {
                String value = property.getValue(String.class).orElse(null);
                if (StringUtils.isNotEmpty(value)) {
                    String methodName = "with" + NameUtils.capitalize(name);
                    Optional<Method> method = ReflectionUtils.findMethodsByName(setterClass, methodName)
                        .findFirst();
                    if (method.isPresent()) {
                        Method m = method.get();
                        Class<?>[] parameterTypes = m.getParameterTypes();
                        if (parameterTypes.length == 1) {
                            Optional<?> converted = ConversionService.SHARED.convert(value, parameterTypes[0]);
                            if (converted.isPresent()) {

                                try {
                                    Object v = converted.get();
                                    ReflectionUtils.invokeMethod(instance, m, v);
                                } catch (Exception e) {
                                    throw new ConfigurationException("Invalid Hystrix Property: " + name);
                                }
                            }
                        }
                    }
                }

            }
        }
        return instance;
    }

    @SuppressWarnings("Duplicates")
    private HystrixObservableCommand.Setter buildObservableSetter(String hystrixGroup, String commandName, AnnotationValue<Hystrix> settings) {
        HystrixObservableCommand.Setter setter = HystrixObservableCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(hystrixGroup));
        HystrixCommandKey commandKey = HystrixCommandKey.Factory.asKey(commandName);
        setter.andCommandKey(commandKey);

        if (settings != null) {
            List<AnnotationValue<Property>> properties = settings.getAnnotations("commandProperties", Property.class);
            if (!properties.isEmpty()) {
                HystrixCommandProperties.Setter instance = buildCommandProperties(properties);
                setter.andCommandPropertiesDefaults(
                    instance
                );
            }

        }
        return setter;
    }

    private String resolveHystrixGroup(MethodInvocationContext<Object, Object> context,
                                       AnnotationValue<Hystrix> settings) {

        Supplier<String> groupSupplier = () ->
                context.getValue("io.micronaut.http.client.Client", "id", String.class)
                        .orElse(context.getDeclaringType().getSimpleName());

        if (settings != null) {
            return settings.get("group", String.class).orElseGet(groupSupplier);
        } else {
            return groupSupplier.get();
        }
    }
}
