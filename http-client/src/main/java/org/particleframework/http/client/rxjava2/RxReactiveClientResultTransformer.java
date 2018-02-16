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
package org.particleframework.http.client.rxjava2;

import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.particleframework.context.annotation.Requires;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.client.ReactiveClientResultTransformer;
import org.particleframework.http.client.exceptions.HttpClientException;
import org.particleframework.http.client.exceptions.HttpClientResponseException;
import org.particleframework.inject.ExecutionHandle;
import org.particleframework.inject.MethodExecutionHandle;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Adds custom support for {@link Maybe} to handle NOT_FOUND results
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(classes = Flowable.class)
public class RxReactiveClientResultTransformer implements ReactiveClientResultTransformer {
    @Override
    public Object transform(Object publisherResult, Supplier<Optional<MethodExecutionHandle<Object>>> fallbackResolver, Object...parameters) {
        if(publisherResult instanceof Maybe) {
            Maybe<?> maybe = (Maybe) publisherResult;
            // add 404 handling for maybe
            return maybe.onErrorResumeNext(throwable -> {
                if(throwable instanceof HttpClientResponseException) {
                    HttpClientResponseException responseException = (HttpClientResponseException) throwable;
                    if(responseException.getStatus() == HttpStatus.NOT_FOUND) {
                        return Maybe.empty();
                    }
                }

                Optional<MethodExecutionHandle<Object>> fallback = fallbackResolver.get();
                if(fallback.isPresent()) {
                    ExecutionHandle<Object> fallbackHandle = fallback.get();
                    Object result = fallbackHandle.invoke(parameters);
                    return ConversionService.SHARED.convert(result, Maybe.class).orElseThrow(()-> new HttpClientException("Fallback for method "+fallbackHandle+" returned invalid Reactive type: " +  result));
                }
                else {
                    return Maybe.error(throwable);
                }
            });
        }
        else if(publisherResult instanceof Single) {
            Single<?> single = (Single) publisherResult;
            return single.onErrorResumeNext(throwable -> {
                Optional<MethodExecutionHandle<Object>> fallback = fallbackResolver.get();
                if(fallback.isPresent()) {
                    ExecutionHandle<Object> fallbackHandle = fallback.get();
                    Object result = fallbackHandle.invoke(parameters);
                    return ConversionService.SHARED.convert(result, Single.class).orElseThrow(()-> new HttpClientException("Fallback for method "+fallbackHandle+" returned invalid Reactive type: " +  result));
                }
                else {
                    return Single.error(throwable);
                }
            });
        }
        else if(publisherResult instanceof Flowable) {
            Flowable<?> single = (Flowable) publisherResult;
            return single.onErrorResumeNext(throwable -> {
                Optional<MethodExecutionHandle<Object>> fallback = fallbackResolver.get();
                if(fallback.isPresent()) {
                    ExecutionHandle<Object> fallbackHandle = fallback.get();
                    Object result = fallbackHandle.invoke(parameters);
                    return ConversionService.SHARED.convert(result, Flowable.class).orElseThrow(()-> new HttpClientException("Fallback for method "+fallbackHandle+" returned invalid Reactive type: " +  result));
                }
                else {
                    return Flowable.error(throwable);
                }
            });
        }
        return publisherResult;
    }
}
