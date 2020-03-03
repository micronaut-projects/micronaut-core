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

import java.net.URI;

/**
 * Deprecated. Please use io.micronaut.http.hateoas.DefaultLink
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Deprecated
class DefaultLink extends io.micronaut.http.hateoas.DefaultLink implements Link.Builder {

    /**
     * @param uri The URI
     * @deprecated Use {@link io.micronaut.http.hateoas.DefaultLink} instead
     */
    protected DefaultLink(URI uri) {
        super(uri);
    }

    @Override
    public Link.Builder templated(boolean templated) {
        return (Link.Builder) super.templated(templated);
    }

    @Override
    public Link.Builder profile(URI profile) {
        return (Link.Builder) super.profile(profile);
    }

    @Override
    public Link.Builder deprecation(URI deprecation) {
        return (Link.Builder) super.deprecation(deprecation);
    }

    @Override
    public Link.Builder title(String title) {
        return (Link.Builder) super.title(title);
    }

    @Override
    public Link.Builder name(String name) {
        return (Link.Builder) super.name(name);
    }

    @Override
    public Link.Builder hreflang(String hreflang) {
        return (Link.Builder) super.hreflang(hreflang);
    }

    @Override
    public Link.Builder type(MediaType mediaType) {
        return (Link.Builder) super.type(mediaType);
    }

    @Override
    public Link build() {
        return (Link) super.build();
    }
}
