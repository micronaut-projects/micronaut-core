/*
 * Copyright 2017 original authors
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
package org.particleframework.core.naming.conventions;

import java.util.Locale;
import java.util.Optional;

/**
 * <p>Represents the built in </p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public enum MethodConvention {

    /**
     * The index method of controllers
     */
    INDEX("GET"),

    /**
     * The show method of controllers
     */
    SHOW("GET"),

    /**
     * The show method of controllers
     */
    SAVE("POST"),

    /**
     * The default update method of controllers
     */
    UPDATE("PUT"),

    /**
     * The default delete method of controllers
     */
    DELETE,

    /**
     * The default options method of controllers
     */
    OPTIONS,

    /**
     * The default head method of controllers
     */
    HEAD;

    private final String lowerCase;
    private final String httpMethod;

    MethodConvention(String httpMethod) {
        this.httpMethod = httpMethod;
        this.lowerCase = name().toLowerCase(Locale.ENGLISH);
    }

    MethodConvention() {
        this.httpMethod = name();
        this.lowerCase = name().toLowerCase(Locale.ENGLISH);
    }

    /**
     * The HTTP method name for this convention
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
     * Obtain the method convention for the given method
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
