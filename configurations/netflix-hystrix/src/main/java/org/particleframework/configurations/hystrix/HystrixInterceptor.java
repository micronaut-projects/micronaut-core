/*
 * Copyright 2018 original authors
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
package org.particleframework.configurations.hystrix;

import com.netflix.hystrix.*;
import org.particleframework.aop.InterceptPhase;
import org.particleframework.aop.MethodInterceptor;
import org.particleframework.aop.MethodInvocationContext;
import org.particleframework.core.async.publisher.Publishers;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.type.ReturnType;
import org.particleframework.core.util.StringUtils;
import rx.Observable;
import rx.SingleSubscriber;

import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * A {@link MethodInterceptor} that adds support for decorating methods for Hystrix
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class HystrixInterceptor implements MethodInterceptor<Object, Object> {

    public static final String MEMBER_THREAD_POOL = "threadPool";
    public static final String MEMBER_GROUP = "group";
    public static final int POSITION = InterceptPhase.RETRY.getPosition() + 10;

    @Override
    public int getOrder() {
        return POSITION;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Class<org.particleframework.configurations.hystrix.annotation.HystrixCommand> annotationType = org.particleframework.configurations.hystrix.annotation.HystrixCommand.class;
        org.particleframework.configurations.hystrix.annotation.Hystrix settings = context.getAnnotation(org.particleframework.configurations.hystrix.annotation.Hystrix.class);
        org.particleframework.configurations.hystrix.annotation.HystrixCommand cmd = context.getAnnotation(annotationType);
        if(cmd == null) {
            return context.proceed();
        }
        else {

            String hystrixGroup = resolveHystrixGroup(context, settings);
            String commandName = cmd.value();
            if(StringUtils.isEmpty(commandName)) {
                commandName = context.getMethodName();
            }
            boolean hasSettings = settings != null;
            String threadPool = hasSettings ? settings.threadPool() : null;
            boolean wrapExceptions = hasSettings && settings.wrapExceptions();

            ReturnType<Object> returnType = context.getReturnType();
            Class<Object> javaReturnType = returnType.getType();

            boolean isFuture = CompletableFuture.class.isAssignableFrom(javaReturnType);
            if(Publishers.isPublisher(javaReturnType) || isFuture) {
                HystrixObservableCommand.Setter setter = buildObservableSetter(hystrixGroup, commandName);

                HystrixObservableCommand<Object> command = new HystrixObservableCommand<Object>(setter) {
                    @SuppressWarnings("unchecked")
                    @Override
                    protected Observable<Object> construct() {
                        Object result = context.proceed();
                        return ConversionService.SHARED.convert(result, Observable.class)
                                .orElseThrow(()->new IllegalStateException("Unsupported Reactive type: " + javaReturnType)) ;
                    }

                    @Override
                    protected boolean isFallbackUserDefined() {
                        return super.isFallbackUserDefined();
                    }

                    @Override
                    protected Observable<Object> resumeWithFallback() {
                        return super.resumeWithFallback();
                    }

                    @Override
                    protected boolean shouldNotBeWrapped(Throwable underlying) {
                        return !wrapExceptions || super.shouldNotBeWrapped(underlying);
                    }
                };
                if(isFuture) {
                    CompletableFuture future = new CompletableFuture();
                    command.toObservable().toSingle().subscribe(new SingleSubscriber<Object>() {
                        @Override
                        public void onSuccess(Object value) {
                            future.complete(value);
                        }

                        @Override
                        public void onError(Throwable error) {
                            future.completeExceptionally(error);
                        }
                    });
                    return future;
                }
                else {
                    return ConversionService.SHARED.convert(command.toObservable(), returnType.asArgument())
                            .orElseThrow(()-> new IllegalStateException("Unsupported Reactive type: " + javaReturnType));
                }
            }
            else {
                HystrixCommand.Setter setter = HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(hystrixGroup));
                setter.andCommandKey(HystrixCommandKey.Factory.asKey(commandName));
                if(StringUtils.isNotEmpty(threadPool)) {
                    setter.andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey(threadPool));
                }

                HystrixCommand<Object> hystrixCommand = new HystrixCommand<Object>(setter) {
                    @Override
                    protected Object run() throws Exception {
                        return context.proceed();
                    }

                    @Override
                    protected boolean isFallbackUserDefined() {
                        return super.isFallbackUserDefined();
                    }

                    @Override
                    protected Object getFallback() {
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

                    if(!wrapExceptions) {
                        // unpack the original exception
                        //noinspection ConstantConditions
                        if(e instanceof ExecutionException) {
                            Throwable cause = e.getCause();
                            if(cause instanceof RuntimeException) {
                                throw (RuntimeException)cause;
                            }
                        }
                    }
                    throw e;
                }
            }
        }
    }

    private HystrixObservableCommand.Setter buildObservableSetter(String hystrixGroup, String commandName) {
        HystrixObservableCommand.Setter setter = HystrixObservableCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(hystrixGroup));
        return setter.andCommandKey(HystrixCommandKey.Factory.asKey(commandName));
    }

    private String resolveHystrixGroup(MethodInvocationContext<Object, Object> context,
                                       org.particleframework.configurations.hystrix.annotation.Hystrix ann) {
        String group = ann != null ? ann.group() : null;
        if(StringUtils.isEmpty(group)) {
            return context.getValue("org.particleframework.http.client.Client", "id", String.class).orElse(context.getDeclaringType().getSimpleName());
        }
        return group;
    }
}
