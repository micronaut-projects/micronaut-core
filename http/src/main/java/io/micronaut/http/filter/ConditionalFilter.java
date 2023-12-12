/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.filter;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpRequest;

/**
 * The condition for instances of {@link io.micronaut.http.annotation.RequestFilter} and {@link io.micronaut.http.annotation.ResponseFilter} filters.
 * Allowing to skip all the method filters if {@link #isEnabled(HttpRequest)} returns false.
 *
 * @author Denis Stepanov
 * @since 4.3.0
 */
@Experimental
public interface ConditionalFilter {

    /**
     * The filter condition.
     *
     * @param request The request
     * @return true if the filter is enabled
     */
    boolean isEnabled(HttpRequest<?> request);

}
