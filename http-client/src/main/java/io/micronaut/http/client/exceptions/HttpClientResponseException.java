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

package io.micronaut.http.client.exceptions;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.hateos.JsonError;
import io.micronaut.http.hateos.VndError;

import java.util.Optional;

/**
 * An exception that occurs when a response returns an error code equal to or greater than 400.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class HttpClientResponseException extends HttpClientException {
    private final HttpResponse<?> response;
    private boolean jsonType = true;

    /**
     * @param message  The message
     * @param response The Http response
     */
    public HttpClientResponseException(String message, HttpResponse<?> response) {
        super(message);
        this.response = response;
        initResponse(response);
    }

    /**
     * @param message  The message
     * @param cause    The throwable
     * @param response The Http response
     */
    public HttpClientResponseException(String message, Throwable cause, HttpResponse<?> response) {
        super(message, cause);
        this.response = response;
        initResponse(response);
    }

    @Override
    public String getMessage() {
        if (jsonType) {
            return getResponse().getBody(JsonError.class).map(JsonError::getMessage).orElse(super.getMessage());
        } else {
            return getResponse().getBody(String.class).orElse(super.getMessage());
        }
    }

    /**
     * @return The {@link HttpResponse}
     */
    public HttpResponse<?> getResponse() {
        return response;
    }

    /**
     * @return The {@link HttpStatus} returned
     */
    public HttpStatus getStatus() {
        return getResponse().getStatus();
    }

    @SuppressWarnings("MagicNumber")
    private void initResponse(HttpResponse<?> response) {
        Optional<MediaType> contentType = response.getContentType();
        if (contentType.isPresent() && response.getStatus().getCode() > 399) {
            if (contentType.get().equals(MediaType.APPLICATION_JSON_TYPE)) {
                response.getBody(JsonError.class);
            } else if (contentType.get().equals(MediaType.APPLICATION_VND_ERROR_TYPE)) {
                response.getBody(VndError.class);
            } else {
                jsonType = false;
                response.getBody(String.class);
            }
        }
    }
}
