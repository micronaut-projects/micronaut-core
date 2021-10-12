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

/**
 * Parent class of all exceptions thrown during HTTP processing.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class HttpException extends RuntimeException {

    /**
     * Default constructor.
     */
    public HttpException() {
    }

    /**
     * @param message The message
     */
    public HttpException(String message) {
        super(message);
    }

    /**
     * @param message The message
     * @param cause   The throwable
     */
    public HttpException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message The message
     * @param cause   The throwable
     * @param enableSuppression Enable suppression
     * @param writableStackTrace Writable stacktrace
     */
    protected HttpException(String message, Throwable cause,
                               boolean enableSuppression,
                               boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
