/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.http.MediaType;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.URI;
import java.util.Optional;

/**
 * <p>Interface for a hateoas link.</p>
 * <p>
 * <p>See https://tools.ietf.org/html/draft-kelly-json-hal-08#section-5</p>
 *
 * @author Graeme Rocher
 * @since 1.1
 */
public interface Link {

    /**
     * Help link.
     */
    CharSequence HELP = "help";

    /**
     * Self link.
     */
    CharSequence SELF = "self";

    /**
     * About link.
     */
    CharSequence ABOUT = "about";

    /**
     * Href link.
     */
    CharSequence HREF = "href";

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
     * Create a link from the given URI.
     *
     * @param uri The URI
     * @return The link
     */
    static Link of(URI uri) {
        return new DefaultLink(uri);
    }

    /**
     * Create a link from the given URI.
     *
     * @param uri The URI
     * @return The link
     */
    static Link of(String uri) {
        return new DefaultLink(URI.create(uri));
    }

    /**
     * Create a link from the given URI.
     *
     * @param uri The URI
     * @return The link
     */
    static Link.Builder build(URI uri) {
        return new DefaultLink(uri);
    }

    /**
     * Build for creating {@link Link} instances.
     */
    interface Builder {

        /**
         * @param templated Whether the URI is templated
         * @return The builder
         * @see Link#isTemplated()
         */
        Builder templated(boolean templated);

        /**
         * @param profile The profile URI
         * @return The builder
         * @see Link#getProfile()
         */
        Builder profile(@Nullable URI profile);

        /**
         * @param deprecation The deprecation URI
         * @return The builder
         * @see Link#getDeprecation()
         */
        Builder deprecation(@Nullable URI deprecation);

        /**
         * @param title The title of the link
         * @return The builder
         * @see Link#getTitle()
         */
        Builder title(@Nullable String title);

        /**
         * @param name The name of the link
         * @return The builder
         * @see Link#getName()
         */
        Builder name(@Nullable String name);

        /**
         * @param hreflang The language of the link
         * @return The builder
         * @see Link#getHreflang()
         */
        Builder hreflang(@Nullable String hreflang);

        /**
         * @param mediaType The type of the URI
         * @return The builder
         * @see Link#getType()
         */
        Builder type(@Nullable MediaType mediaType);

        /**
         * Build the link.
         *
         * @return The {@link Link}
         */
        Link build();
    }
}
