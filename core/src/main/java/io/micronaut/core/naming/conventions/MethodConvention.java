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
package io.micronaut.core.naming.conventions;

import io.micronaut.core.annotation.Experimental;

import java.util.Locale;
import java.util.Optional;

/**
 * <p>Represents the built in conventions for mapping a method name to an HTTP Method and URI.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Experimental
public enum MethodConvention {

    /**
     * The index method of controllers.
     */
    INDEX("", "GET"),

    /**
     * The show method of controllers.
     */
    SHOW(MethodConvention.ID_PATH, "GET"),

    /**
     * The show method of controllers.
     */
    SAVE("", "POST"),

    /**
     * The default update method of controllers.
     */
    UPDATE(MethodConvention.ID_PATH, "PUT"),

    /**
     * The default delete method of controllers.
     */
    DELETE(MethodConvention.ID_PATH),

    /**
     * The default options method of controllers.
     */
    OPTIONS(""),

    /**
     * The default head method of controllers.
     */
    HEAD(""),

    /**
     * The default trace method of controllers.
     */
    TRACE("");

    /**
     * Path for the id.
     */
    public static final String ID_PATH = "{/id}";

    private final String lowerCase;
    private final String httpMethod;
    private final String uri;

    /**
     * @param uri        The URI
     * @param httpMethod The Http method
     */
    MethodConvention(String uri, String httpMethod) {
        this.uri = uri;
        this.httpMethod = httpMethod;
        this.lowerCase = name().toLowerCase(Locale.ENGLISH);
    }

    /**
     * @param uri The URI
     */
    MethodConvention(String uri) {
        this.uri = uri;
        this.httpMethod = name();
        this.lowerCase = name().toLowerCase(Locale.ENGLISH);
    }

    /**
     * @return The default URI to map to if non is specified
     */
    public String uri() {
        return this.uri;
    }

    /**
     * @return The HTTP method name for this convention.
     */
    public String httpMethod() {
        return httpMethod;
    }

    /**
     * @return The method name for this convention
     */
    public String methodName() {
        return this.lowerCase;
    }

    /**
     * Obtain the method convention for the given method.
     *
     * @param name The method name
     * @return An optional of the method convention
     */
    public static Optional<MethodConvention> forMethod(String name) {
        try {
            return Optional.of(valueOf(name.toUpperCase(Locale.ENGLISH)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
