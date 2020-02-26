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
import io.micronaut.core.naming.NameUtils;

import java.util.Locale;

/**
 * <p>Common conventions for types</p>.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Experimental
public enum TypeConvention {
    CONTROLLER,
    SERVICE,
    REPOSITORY,
    JOB,
    FACTORY;

    private final String suffix;

    /**
     * Default constructor.
     */
    TypeConvention() {
        this.suffix = NameUtils.capitalize(name().toLowerCase(Locale.ENGLISH));
    }

    /**
     * <p>Returns the property name equivalent for this convention for the given class. Eg. BookController -&gt; book</p>
     *
     * @param type The type
     * @return The property name equivalent
     */
    public String asPropertyName(Class type) {
        return NameUtils.decapitalizeWithoutSuffix(type.getSimpleName(), suffix);
    }

    /**
     * <p>Returns the hyphenated equivalent for this convention for the given class. Eg. BookShopController -&gt; book-shop</p>
     *
     * @param type The type
     * @return The property name equivalent
     */
    public String asHyphenatedName(Class type) {
        String shortName = NameUtils.trimSuffix(type.getSimpleName(), suffix);
        return NameUtils.hyphenate(shortName);
    }

}
