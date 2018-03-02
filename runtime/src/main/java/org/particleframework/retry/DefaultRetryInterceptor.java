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
package org.particleframework.retry;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.functions.Function;
import org.particleframework.aop.InterceptPhase;
import org.particleframework.aop.MethodInterceptor;
import org.particleframework.aop.MethodInvocationContext;
import org.particleframework.core.async.publisher.Publishers;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.value.ConvertibleValues;
import org.particleframework.core.type.ReturnType;
import org.particleframework.retry.annotation.Retry;

import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * A {@link MethodInterceptor} that retries that operation according to the specified
 * {@link org.particleframework.retry.annotation.Retry} annotation
 *
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class DefaultRetryInterceptor implements MethodInterceptor<Object,Object> {

    private static final String ATTEMPTS = "attempts";
    private static final String MULTIPLIER = "multiplier";
    private static final String DELAY = "delay";
    private static final String MAX_DELAY = "maxDelay";

    @Override
    public int getOrder() {
        return InterceptPhase.RETRY.getPosition();
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        ConvertibleValues<?> retry = context.getValues(Retry.class);
        if(retry != null) {
            int attempts = retry.get(ATTEMPTS, Integer.class).orElse(3);
            Duration delay = retry.get(DELAY, Duration.class).orElse(Duration.ofSeconds(1));

            RetryContext retryContext = new RetryContext(
                    attempts,
                    retry.get(MULTIPLIER, Double.class).orElse(0d),
                    delay,
                    retry.get(MAX_DELAY, Duration.class).orElse(null)
            );
            context.getAttributes().put(RetryContext.class.getName(), retry);

            ReturnType<Object> returnType = context.getReturnType();
            Class<Object> javaReturnType = returnType.getType();
            if(Publishers.isPublisher(javaReturnType)) {
                ConversionService<?> conversionService = ConversionService.SHARED;
                Object result = context.proceed();
                if(result == null) {
                    return result;
                }
                else {
                    Flowable observable = conversionService.convert(result, Flowable.class)
                                                         .orElseThrow(()-> new IllegalStateException("Unconvertible Reactive type: " + result));
                    Flowable retryObservable = observable.onErrorResumeNext(retryFlowable(retryContext, observable));

                    return conversionService.convert(retryObservable, returnType.asArgument())
                            .orElseThrow(()-> new IllegalStateException("Unconvertible Reactive type: " + result));
                }

            }
            int retryCount = 0;
            while(retryCount < attempts) {
                try {
                    retryCount = retryContext.incrementAttempts();
                    return context.proceed();
                } catch (RuntimeException e) {
                    if(!retryContext.canRetry()) {
                        throw e;
                    }
                    else {
                        long delayMillis = retryContext.nextDelay();
                        try {
                            Thread.sleep(delayMillis);
                        } catch (InterruptedException e1) {
                            throw e;
                        }
                    }
                }
            }
            throw new IllegalStateException("Retry Interceptor exited retry loop illegally");
        }
        else {
            return context.proceed();
        }
    }

    @SuppressWarnings("unchecked")
    private Function retryFlowable(RetryContext retryContext, Flowable observable) {
        return throwable -> {
            if(retryContext.canRetry()) {
                retryContext.incrementAttempts();
                Flowable retryObservable;
                if(retryContext.canRetry()) {
                    retryObservable = observable.onErrorResumeNext(retryFlowable(retryContext, observable));
                }
                else {
                    retryObservable = observable;
                }
                long delay = retryContext.nextDelay();
                return retryObservable.delay(delay, TimeUnit.MILLISECONDS);
            }
            return Observable.error((Throwable) throwable);
        };
    }
}
