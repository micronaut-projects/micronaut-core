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
package org.particleframework.http.client.exceptions;

import org.particleframework.http.HttpResponse;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.MediaType;
import org.particleframework.http.hateos.VndError;

import java.util.Optional;

/**
 * An exception that occurs when a response returns an error code equal to or greater than 400
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class HttpClientResponseException extends HttpClientException{
    private final HttpResponse<?> response;

    public HttpClientResponseException(String message, HttpResponse<?> response) {
        super(message);
        this.response = response;
        initResponse(response);
    }

    public HttpClientResponseException(String message, Throwable cause, HttpResponse<?> response) {
        super(message, cause);
        this.response = response;
        initResponse(response);
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

    private void initResponse(HttpResponse<?> response) {
        Optional<MediaType> contentType = response.getContentType();
        if(contentType.isPresent() && contentType.get().equals(MediaType.APPLICATION_VND_ERROR_TYPE)) {
            // initialize the body so it is available
            response.getBody(VndError.class);
        }
    }
}
