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

package io.micronaut.http.bind.binders;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpParameters;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.pagination.PageImpl;
import io.micronaut.http.pagination.Pageable;
import io.micronaut.http.pagination.PaginationConfiguration;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.function.Supplier;

import static java.lang.Integer.max;
import static java.lang.Integer.min;

/**
 * An {@link io.micronaut.http.bind.binders.TypedRequestArgumentBinder} implementation that binds a
 * {@link io.micronaut.http.pagination.Pageable} with pagination information.
 *
 * @author boros
 * @since 1.0.1
 */
@Singleton
public class PageableArgumentBinder implements TypedRequestArgumentBinder<Pageable> {

    private final PaginationConfiguration.PaginationSizeConfiguration sizeConfiguration;
    private final PaginationConfiguration.PaginationOffsetConfiguration offsetConfiguration;

    /**
     * @param paginationConfiguration the pagination configuration
     */
    public PageableArgumentBinder(PaginationConfiguration paginationConfiguration) {
        this.sizeConfiguration = paginationConfiguration.getSizeConfiguration();
        this.offsetConfiguration = paginationConfiguration.getOffsetConfiguration();
    }

    @Override
    public BindingResult<Pageable> bind(ArgumentConversionContext<Pageable> context, HttpRequest<?> source) {
        HttpParameters parameters = source.getParameters();

        int size = Optional.ofNullable(parameters.get(sizeConfiguration.getName()))
                .map(s -> parse(() -> Integer.parseInt(s)))
                .map(l -> min(max(l, sizeConfiguration.getMin()), sizeConfiguration.getMax()))
                .orElse(sizeConfiguration.getDefault());

        long offset = Optional.ofNullable(parameters.get(offsetConfiguration.getName()))
                .map(s -> parse(() -> Long.parseLong(s)))
                .orElse(offsetConfiguration.getDefault());

        return () -> Optional.of(new PageImpl(offset, size));
    }

    @Override
    public Argument<Pageable> argumentType() {
        return Argument.of(Pageable.class);
    }

    private <T> T parse(Supplier<T> parser) {
        try {
            return parser.get();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

}
