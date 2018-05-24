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

package io.micronaut.security.handlers;

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;

/**
 * Handles the rejection of route by {@link io.micronaut.security.filters.SecurityFilter} with the response of correspondent HTTP Status coded.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Primary
@Singleton
public class HttpStatusCodeRejectionHandler implements RejectionHandler {

    /**
     * Constructor.
     */
    public HttpStatusCodeRejectionHandler() {

    }

    /**
     *
     * @param request {@link HttpRequest} being processed
     * @param forbidden if true indicates that although the user was authenticated, he did not have the necessary access priviledges.
     * @return Return a HTTP Response
     */
    @Override
    public Publisher<MutableHttpResponse<?>> reject(HttpRequest<?> request, boolean forbidden) {
        return Publishers.just(HttpResponse.status(forbidden ? HttpStatus.FORBIDDEN :
                HttpStatus.UNAUTHORIZED));
    }
}
