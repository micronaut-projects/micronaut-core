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
package io.micronaut.annotation.processing;

/**
 * Exception to indicate postponing processing to next round.
 */
public final class PostponeToNextRoundException extends RuntimeException {

    private final transient Object errorElement;
    private final String path;

    /**
     * @param originatingElement Teh originating element
     * @param path The originating element path
     */
    public PostponeToNextRoundException(Object originatingElement, String path) {
        this.errorElement = originatingElement;
        this.path = path;
    }

    public Object getErrorElement() {
        return errorElement;
    }

    public String getPath() {
        return path;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        // no-op: flow control exception
        return this;
    }
}
