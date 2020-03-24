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
package io.micronaut.http.server.exceptions;

import io.micronaut.context.BeanContext;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.hateoas.JsonError;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import javax.inject.Inject;

import javax.inject.Singleton;

/**
 * Handles exception of type {@link java.util.concurrent.CompletionException}, with special care when the cause is a
 * {@link io.micronaut.http.exceptions.HttpStatusException}.
 *
 * @author ratcashdev
 * @since 1.3.x
 */
@Singleton
@Produces
public class CompletableHttpStatusHandler implements ExceptionHandler<CompletionException, HttpResponse> {

    @Inject
    HttpStatusHandler statusHandler;

    @Inject
    BeanContext beanCtx;

    @Override
    public HttpResponse handle(HttpRequest request, CompletionException exception) {
        Throwable cause = exception.getCause();
        if (cause != null) {
            if (cause instanceof HttpStatusException) {
                return statusHandler.handle(request, (HttpStatusException) cause);
            }

            // try to get a specific handler for the class of the 'cause'
            Optional<ExceptionHandler> delegateHandler = getBestHandler(cause.getClass(), Object.class);
            if (delegateHandler.isPresent()) {
                return toHttpResponse(delegateHandler.get().handle(request, cause));
            }
        }
        
        return HttpResponse.serverError()
                .body(new JsonError("Internal Server Error: " + exception.getMessage()));
    }

    private Optional<ExceptionHandler> getBestHandler(Class<?> clazz1, Class<?> clazz2) {
        return beanCtx.findBean(ExceptionHandler.class, Qualifiers.byTypeArgumentsClosest(clazz1, clazz2));
    }

    private HttpResponse toHttpResponse(Object result) {
        if (result instanceof HttpResponse) {
            return (HttpResponse) result;
        }
        if (result instanceof HttpStatus) {
            return HttpResponse.status((HttpStatus) result);
        }
        return HttpResponse.serverError().body(result);
    }
}
