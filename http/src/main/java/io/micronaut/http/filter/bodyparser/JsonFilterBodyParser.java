/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.filter.bodyparser;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.MediaType;

/**
 * Implementation of {@link FilterBodyParser} for a request with JSON content type.
 * @author Sergio del Amo
 * @since 4.7.1
 * @param <T> Body type
 */
@Experimental
public interface JsonFilterBodyParser<T> extends FilterBodyParser<T> {
    @Override
    default MediaType getContentType() {
        return MediaType.APPLICATION_JSON_TYPE;
    }
}
