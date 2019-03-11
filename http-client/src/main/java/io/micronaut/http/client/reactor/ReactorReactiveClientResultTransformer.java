/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.client.reactor;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.ReactiveClientResultTransformer;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.inject.Singleton;

/**
 * Adds custom support for {@link Mono} to handle NOT_FOUND results.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(classes = Mono.class)
@Internal
public class ReactorReactiveClientResultTransformer implements ReactiveClientResultTransformer {

    @Override
    public Object transform(
        Object publisherResult) {
        if (publisherResult instanceof Mono) {
            Mono<?> maybe = (Mono) publisherResult;
            // add 404 handling for maybe
            return maybe.onErrorResume(throwable -> {
                if (throwable instanceof HttpClientResponseException) {
                    HttpClientResponseException responseException = (HttpClientResponseException) throwable;
                    if (responseException.getStatus() == HttpStatus.NOT_FOUND) {
                        return Mono.empty();
                    }
                }
                return Mono.error(throwable);
            });
        } else if (publisherResult instanceof Flux) {
            Flux<?> flux = (Flux) publisherResult;

            return flux.onErrorResume(throwable -> {
                    if (throwable instanceof HttpClientResponseException) {
                        HttpClientResponseException responseException = (HttpClientResponseException) throwable;
                        if (responseException.getStatus() == HttpStatus.NOT_FOUND) {
                            return Flux.empty();
                        }
                    }
                    return Flux.error(throwable);
                }
            );
        } else {
            return publisherResult;
        }
    }
}
