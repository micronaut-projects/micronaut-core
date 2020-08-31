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
package io.micronaut.http.client.bind;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.http.MutableHttpRequest;

/**
 * A binder that binds to a {@link MutableHttpRequest}.
 *
 * @param <T> A type
 * @author James Kleeh
 * @since 2.1.0
 */
@Experimental
public interface ClientArgumentRequestBinder<T> {

    /**
     * Bind the given argument to the request.
     *
     * @param context The {@link ArgumentConversionContext}
     * @param value   The argument value
     * @param request The request
     */
    void bind(ArgumentConversionContext<T> context, T value, MutableHttpRequest<?> request);

}
