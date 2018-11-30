/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.jackson;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;

public enum JacksonPropertyNamingStrategy {
    SNAKE_CASE(PropertyNamingStrategy.SNAKE_CASE),
    UPPER_CAMEL_CASE(PropertyNamingStrategy.UPPER_CAMEL_CASE),
    LOWER_CAMEL_CASE(PropertyNamingStrategy.LOWER_CAMEL_CASE),
    LOWER_CASE(PropertyNamingStrategy.LOWER_CASE),
    KEBAB_CASE(PropertyNamingStrategy.KEBAB_CASE);

    final PropertyNamingStrategy propertyNamingStrategy;

    JacksonPropertyNamingStrategy(PropertyNamingStrategy propertyNamingStrategy) {
        this.propertyNamingStrategy = propertyNamingStrategy;
    }
}
