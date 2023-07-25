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
package io.micronaut.http.bind.binders;

import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.http.HttpRequest;

import java.util.Optional;

/**
 * Marker interface for {@link RequestArgumentBinder} to indicate that it should bind after filters are applied.
 * @param <T> A type
 * @author Denis Stepanov
 * @since 4.0.0
 */
public interface PostponedRequestArgumentBinder<T> extends RequestArgumentBinder<T> {

    /**
     * Bind postponed the given argument from the given source.
     *
     * @param context The {@link ArgumentConversionContext}
     * @param request The request
     * @return An {@link Optional} of the value. If no binding was possible {@link Optional#empty()}
     */
    default ArgumentBinder.BindingResult<T> bindPostponed(ArgumentConversionContext<T> context, HttpRequest<?> request) {
        return bind(context, request);
    }

}
