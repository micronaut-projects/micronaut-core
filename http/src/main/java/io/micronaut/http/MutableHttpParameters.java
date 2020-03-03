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
package io.micronaut.http;

import java.util.Collections;
import java.util.List;

/**
 * Mutable version of {@link HttpParameters} which allows adding new parameters.
 *
 * @author Vladimir Orany
 * @since 1.0
 */
public interface MutableHttpParameters extends HttpParameters {

    /**
     * Adds a new http parameter.
     *
     * @param name      the name of the parameter
     * @param value     the value of the parameter
     * @return self
     */
    default MutableHttpParameters add(CharSequence name, CharSequence value) {
        add(name, Collections.singletonList(value));
        return this;
    }

    /**
     * Adds a new http parameter.
     *
     * @param name      the name of the parameter
     * @param values    the values of the parameter
     * @return self
     */
    MutableHttpParameters add(CharSequence name, List<CharSequence> values);

}
