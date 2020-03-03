/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.client.rxjava2;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.ReactiveClientResultTransformer;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.reactivex.Flowable;
import io.reactivex.Maybe;

import javax.inject.Singleton;

/**
 * Adds custom support for {@link Maybe} to handle NOT_FOUND results.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(classes = Flowable.class)
@Internal
public class RxReactiveClientResultTransformer implements ReactiveClientResultTransformer {

    @Override
    public Object transform(
        Object publisherResult) {
        if (publisherResult instanceof Maybe) {
            Maybe<?> maybe = (Maybe) publisherResult;
            // add 404 handling for maybe
            return maybe.onErrorResumeNext(throwable -> {
                if (throwable instanceof HttpClientResponseException) {
                    HttpClientResponseException responseException = (HttpClientResponseException) throwable;
                    if (responseException.getStatus() == HttpStatus.NOT_FOUND) {
                        return Maybe.empty();
                    }
                }

                return Maybe.error(throwable);
            });
        }
        return publisherResult;
    }
}
