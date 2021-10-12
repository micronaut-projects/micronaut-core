/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.exceptions;

import io.micronaut.http.HttpStatus;

import java.util.Optional;

/**
 * Exception thrown to return an specific HttpStatus and an error message.
 *
 * @author Iván López
 * @since 1.0
 */
public class HttpStatusException extends HttpException {

    private HttpStatus status;
    private Object body;

    /**
     * @param status  The {@link io.micronaut.http.HttpStatus}
     * @param message The message
     */
    public HttpStatusException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * @param status The {@link io.micronaut.http.HttpStatus}
     * @param body   The arbitrary object to return
     */
    public HttpStatusException(HttpStatus status, Object body) {
        this.status = status;
        this.body = body;
    }

    /**
     * @return The {@link io.micronaut.http.HttpStatus}
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * @return The optional body for the response
     */
    public Optional<Object> getBody() {
        return Optional.ofNullable(body);
    }
}
