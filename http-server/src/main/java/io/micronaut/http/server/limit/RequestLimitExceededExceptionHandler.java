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
package io.micronaut.http.server.limit;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ErrorResponseProcessorExceptionHandler;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import jakarta.inject.Singleton;

import java.time.Duration;

/**
 * Answers a {@link RequestLimitExceededException} with its status and, when known, a
 * {@code Retry-After} header in whole seconds, rounded up.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Singleton
@Produces
@Experimental
public class RequestLimitExceededExceptionHandler extends ErrorResponseProcessorExceptionHandler<RequestLimitExceededException> {

    /**
     * @param responseProcessor The error response processor
     */
    public RequestLimitExceededExceptionHandler(ErrorResponseProcessor<?> responseProcessor) {
        super(responseProcessor);
    }

    @Override
    protected MutableHttpResponse<?> createResponse(RequestLimitExceededException exception) {
        MutableHttpResponse<?> response = HttpResponse.status(exception.getStatus());
        exception.getRetryAfter().ifPresent(retryAfter ->
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(seconds(retryAfter))));
        return response;
    }

    private static long seconds(Duration duration) {
        long seconds = duration.getSeconds();
        return duration.getNano() > 0 ? seconds + 1 : Math.max(seconds, 1);
    }
}
