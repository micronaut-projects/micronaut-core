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
package io.micronaut.core.naming.conventions;

import io.micronaut.core.annotation.Experimental;

import java.util.Locale;

/**
 * <p>Typical conventions used for property names through the system.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Experimental
public enum PropertyConvention {

    /**
     * The ID property used in REST endpoints and object mapping.
     */
    ID;

    private final String lowerCase;

    /**
     * Default constructor.
     */
    PropertyConvention() {
        this.lowerCase = name().toLowerCase(Locale.ENGLISH);
    }

    /**
     * @return The lowercase name
     */
    public String lowerCaseName() {
        return this.lowerCase;
    }
}
