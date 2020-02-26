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
package io.micronaut.http.hateoas;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.MediaType;

import java.net.URI;
import java.util.Optional;

/**
 * Default implementation of {@link Link}.
 *
 * @author Graeme Rocher
 * @since 1.1
 */
@Introspected
public class DefaultLink implements Link, Link.Builder {

    final URI href;
    private boolean templated;
    private URI profile;
    private URI deprecation;
    private String title;
    private String hreflang;
    private MediaType type;
    private String name;

    /**
     * @param uri The URI
     */
    protected DefaultLink(URI uri) {
        this.href = uri;
    }

    @Override
    public URI getHref() {
        return href;
    }

    @Override
    public Builder templated(boolean templated) {
        this.templated = templated;
        return this;
    }

    @Override
    public Builder profile(URI profile) {
        this.profile = profile;
        return this;
    }

    @Override
    public Builder deprecation(URI deprecation) {
        this.deprecation = deprecation;
        return this;
    }

    @Override
    public Builder title(String title) {
        this.title = title;
        return this;
    }

    @Override
    public Builder name(String name) {
        this.name = name;
        return this;
    }

    @Override
    public Builder hreflang(String hreflang) {
        this.hreflang = hreflang;
        return this;
    }

    @Override
    public Builder type(MediaType mediaType) {
        this.type = mediaType;
        return this;
    }

    @Override
    public boolean isTemplated() {
        return templated;
    }

    @Override
    public Optional<MediaType> getType() {
        return type == null ? Optional.empty() : Optional.of(type);
    }

    @Override
    public Optional<URI> getDeprecation() {
        return deprecation == null ? Optional.empty() : Optional.of(deprecation);
    }

    @Override
    public Optional<URI> getProfile() {
        return profile == null ? Optional.empty() : Optional.of(profile);
    }

    @Override
    public Optional<String> getName() {
        return name == null ? Optional.empty() : Optional.of(name);
    }

    @Override
    public Optional<String> getTitle() {
        return title == null ? Optional.empty() : Optional.of(title);
    }

    @Override
    public Optional<String> getHreflang() {
        return hreflang == null ? Optional.empty() : Optional.of(hreflang);
    }

    @Override
    public Link build() {
        return this;
    }
}
