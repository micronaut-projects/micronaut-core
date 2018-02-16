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
package org.particleframework.http.client.reactor;

import org.particleframework.context.annotation.Requires;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.client.ReactiveClientResultTransformer;
import org.particleframework.http.client.exceptions.HttpClientException;
import org.particleframework.http.client.exceptions.HttpClientResponseException;
import org.particleframework.inject.ExecutionHandle;
import org.particleframework.inject.MethodExecutionHandle;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.function.Supplier;

/**
 *
 * Adds custom support for {@link Mono} to handle NOT_FOUND results
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(classes = Mono.class)
public class ReactorReactiveClientResultTransformer implements ReactiveClientResultTransformer {
    @Override
    public Object transform(Object publisherResult, Supplier<Optional<MethodExecutionHandle<Object>>> fallbackResolver, Object...parameters) {
        if(publisherResult instanceof Mono) {
            Mono<?> maybe = (Mono) publisherResult;
            // add 404 handling for maybe
            return maybe.onErrorResume(throwable -> {
                if(throwable instanceof HttpClientResponseException) {
                    HttpClientResponseException responseException = (HttpClientResponseException) throwable;
                    if(responseException.getStatus() == HttpStatus.NOT_FOUND) {
                        return Mono.empty();
                    }
                }
                Optional<MethodExecutionHandle<Object>> fallback = fallbackResolver.get();
                if(fallback.isPresent()) {
                    ExecutionHandle<Object> fallbackHandle = fallback.get();
                    Object result = fallbackHandle.invoke(parameters);
                    return ConversionService.SHARED.convert(result, Mono.class).orElseThrow(()-> new HttpClientException("Fallback for method "+fallbackHandle+" returned invalid Reactive type: " +  result));
                }
                else {
                    return Mono.error(throwable);
                }
            });
        }
        else if(publisherResult instanceof Flux) {
            Flux<?> flux = (Flux) publisherResult;

            return flux.onErrorResume(throwable -> {
                Optional<MethodExecutionHandle<Object>> fallback = fallbackResolver.get();
                if(fallback.isPresent()) {
                    ExecutionHandle<Object> fallbackHandle = fallback.get();
                    Object result = fallbackHandle.invoke(parameters);
                    return ConversionService.SHARED.convert(result, Flux.class).orElseThrow(()-> new HttpClientException("Fallback for method "+fallbackHandle+" returned invalid Reactive type: " +  result));
                }
                else {
                    return Flux.error(throwable);
                }
            });
        }
        else {
            return publisherResult;
        }
    }
}
