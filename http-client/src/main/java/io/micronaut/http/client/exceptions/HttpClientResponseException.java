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
package io.micronaut.http.client.exceptions;

import io.micronaut.core.type.Argument;
import io.micronaut.http.*;

import java.net.URI;
import java.util.Optional;

/**
 * An exception that occurs when a response returns an error code equal to or greater than 400.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class HttpClientResponseException extends HttpClientException implements HttpResponseProvider {
    private final HttpResponse<?> response;
    private final HttpRequest<?> request;
    private final HttpClientErrorDecoder errorDecoder;

    /**
     * @param message  The message
     * @param response The Http response
     */
    public HttpClientResponseException(String message, HttpRequest<?> request, HttpResponse<?> response) {
        this(message, request, null, response);
    }

    /**
     * @param message  The message
     * @param cause    The throwable
     * @param response The Http response
     */
    public HttpClientResponseException(String message, HttpRequest<?> request, Throwable cause, HttpResponse<?> response) {
        this(message, request, cause, response, HttpClientErrorDecoder.DEFAULT);
    }

    /**
     * @param message  The message
     * @param cause    The throwable
     * @param response The Http response
     * @param errorDecoder The error decoder
     */
    public HttpClientResponseException(String message, HttpRequest<?> request, Throwable cause, HttpResponse<?> response, HttpClientErrorDecoder errorDecoder) {
        super(message, cause);
        this.errorDecoder = errorDecoder;
        this.response = response;
        this.request = request;
        initResponse(response);
    }

    @Override
    public String getMessage() {
        Optional<Argument<?>> errorType = Optional.ofNullable(getErrorType(response));
        return "Request ["+ this.request.getUri().getPath() +"] failed with error: "+ errorType.map(errType ->
             getResponse().getBody(errorType.get()).flatMap(errorDecoder::getMessage).orElse(super.getMessage())
        ).orElse(super.getMessage());
    }

    /**
     * @return The {@link HttpResponse}
     */
    public HttpResponse<?> getResponse() {
        return response;
    }

    /**
     * @return The {@link io.micronaut.http.HttpStatus} returned
     */
    public HttpStatus getStatus() {
        return getResponse().getStatus();
    }

    @SuppressWarnings("MagicNumber")
    private void initResponse(HttpResponse<?> response) {
        Argument<?> errorType = getErrorType(response);
        if (errorType != null) {
            response.getBody(errorType);
        } else {
            response.getBody(String.class);
        }
    }

    private Argument<?> getErrorType(HttpResponse<?> response) {
        Optional<MediaType> contentType = response.getContentType();
        Argument<?> errorType = null;
        if (contentType.isPresent() && response.getStatus().getCode() > 399) {
            MediaType mediaType = contentType.get();
            if (errorDecoder != null) {
                errorType = errorDecoder.getErrorType(mediaType);
            }
        }
        return errorType;
    }
}
