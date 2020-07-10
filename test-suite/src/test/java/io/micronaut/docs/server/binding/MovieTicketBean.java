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
package io.micronaut.docs.server.binding;

// tag::imports[]
import javax.annotation.Nullable;
import javax.validation.constraints.PositiveOrZero;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
// end::imports[]

// tag::class[]
@Introspected
public class MovieTicketBean {

    private HttpRequest<?> httpRequest;

    @PathVariable
    private String movieId;

    @Nullable
    @QueryValue
    @PositiveOrZero
    private Double minPrice;

    @Nullable
    @QueryValue
    @PositiveOrZero
    private Double maxPrice;

    public MovieTicketBean(HttpRequest<?> httpRequest, String movieId, Double minPrice, Double maxPrice) {
        this.httpRequest = httpRequest;
        this.movieId = movieId;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }

    public HttpRequest<?> getHttpRequest() {
        return httpRequest;
    }

    public String getMovieId() {
        return movieId;
    }

    @Nullable
    public Double getMaxPrice() {
        return maxPrice;
    }

    @Nullable
    public Double getMinPrice() {
        return minPrice;
    }
}
// end::class[]
