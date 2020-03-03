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
package io.micronaut.web.router.exceptions;

import io.micronaut.core.type.Argument;

/**
 * An exception thrown when the an {@link io.micronaut.http.annotation.Header} to a {@link io.micronaut.web.router.Route} cannot be satisfied.
 *
 * @author Fabien Renaud
 * @since 1.3.0
 */
public final class UnsatisfiedHeaderRouteException extends UnsatisfiedRouteException {

    private final String name;

    /**
     * @param name     The name of the header
     * @param argument The {@link Argument}
     */
    public UnsatisfiedHeaderRouteException(String name, Argument<?> argument) {
        super("Required Header [" + name + "] not specified", argument);
        this.name = name;
    }

    /**
     * @return The name of the header
     */
    public String getHeaderName() {
        return name;
    }
}
