/*
 * Copyright 2017 original authors
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
import java.util.Optional;

/**
 * <p>Interface for a HATEOS link</p>
 *
 * <p>See https://tools.ietf.org/html/draft-kelly-json-hal-08#section-5</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Link {

    CharSequence HELP = "help";
    CharSequence SELF = "self";
    CharSequence ABOUT = "about";
    /**
     * @return The URI to template to
     */
    URI getHref();

    /**
     * @return Whether the URI is templated
     */
    default boolean isTemplated() {
        return false;
    }

    /**
     * @return The type of the URI
     */
    default Optional<MediaType> getType() {
        return Optional.empty();
    }

    /**
     * @return The deprecation URI
     */
    default Optional<URI> getDeprecation() {
        return Optional.empty();
    }

    /**
     * @return The profile URI
     */
    default Optional<URI> getProfile() {
        return Optional.empty();
    }

    /**
     * @return The name of the link
     */
    default Optional<String> getName() {
        return Optional.empty();
    }

    /**
     * @return The title of the link
     */
    default Optional<String> getTitle() {
        return Optional.empty();
    }

    /**
     * @return The language of the link
     */
    default Optional<String> getHreflang() {
        return Optional.empty();
    }

    /**
     * Create a link from the given URI
     * @param uri The URI
     * @return The link
     */
    static Link of(URI uri) {
        return () -> uri;
    }

    /**
     * Create a link from the given URI
     * @param uri The URI
     * @return The link
     */
    static Link.Builder build(URI uri) {
        return new DefaultLink(uri);
    }

    /**
     * Build for creating {@link Link} instances
     */
    interface Builder {

        /**
         * @see Link#isTemplated()
         */
        Builder templated(boolean templated);

        /**
         * @see Link#getProfile()
         */
        Builder profile(@Nullable URI profile);

        /**
         * @see Link#getDeprecation()
         */
        Builder deprecation(@Nullable URI deprecation);

        /**
         * @see Link#getTitle()
         */
        Builder title(@Nullable String title);

        /**
         * @see Link#getName()
         */
        Builder name(@Nullable String name);

        /**
         * @see Link#getHreflang()
         */
        Builder hreflang(@Nullable String hreflang);

        /**
         * @see Link#getType()
         */
        Builder type(@Nullable MediaType mediaType);

        /**
         * Build the link
         * @return The {@link Link}
         */
        Link build();
    }
}
