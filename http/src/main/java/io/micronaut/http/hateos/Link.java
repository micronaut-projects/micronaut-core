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
package io.micronaut.http.hateos;

import io.micronaut.http.MediaType;

import javax.annotation.Nullable;
import java.net.URI;

/**
 * Deprecated. Please use io.micronaut.http.hateoas.Link
 *
 * @author Graeme Rocher
 * @since 1.0
 * @deprecated Use {@link io.micronaut.http.hateoas.Link} instead
 */
@Deprecated
public interface Link extends io.micronaut.http.hateoas.Link {

    /**
     * Create a link from the given URI.
     *
     * @param uri The URI
     * @return The link
     */
    static Link of(URI uri) {
        return new DefaultLink(uri).build();
    }

    /**
     * Create a link from the given URI.
     *
     * @param uri The URI
     * @return The link
     */
    static io.micronaut.http.hateoas.Link of(String uri) {
        return of(URI.create(uri));
    }

    /**
     * Create a link from the given URI.
     *
     * @param uri The URI
     * @return The link
     */
    static Builder build(URI uri) {
        return new DefaultLink(uri);
    }

    /**
     * @deprecated Use {@link io.micronaut.http.hateoas.Link.Builder} instead.
     */
    @Deprecated
    interface Builder extends io.micronaut.http.hateoas.Link.Builder {
        @Override
        Builder templated(boolean templated);

        @Override
        Builder profile(@Nullable URI profile);

        @Override
        Builder deprecation(@Nullable URI deprecation);

        @Override
        Builder title(@Nullable String title);

        @Override
        Builder name(@Nullable String name);

        @Override
        Builder hreflang(@Nullable String hreflang);

        @Override
        Builder type(@Nullable MediaType mediaType);

        @Override
        Link build();
    }

}
