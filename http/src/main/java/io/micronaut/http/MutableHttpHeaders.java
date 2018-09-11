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

package io.micronaut.http;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Extends {@link HttpHeaders} add methods for mutation of headers.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MutableHttpHeaders extends HttpHeaders {

    /**
     * Add a header for the given name and value.
     *
     * @param header The head name
     * @param value  The value
     * @return This headers object
     */
    MutableHttpHeaders add(CharSequence header, CharSequence value);

    /**
     * Set the allowed HTTP methods.
     *
     * @param methods The methods to specify in the Allowed HTTP header
     * @return This HTTP headers
     */
    default MutableHttpHeaders allow(HttpMethod... methods) {
        return allow(Arrays.asList(methods));
    }

    /**
     * Adds the date header for the given {@link ZonedDateTime}.
     *
     * @param date The local date time (will be converted to GMT) as per {@link DateTimeFormatter#RFC_1123_DATE_TIME}
     * @return The {@link MutableHttpHeaders}
     */
    default MutableHttpHeaders date(LocalDateTime date) {
        if (date != null) {
            add(DATE, ZonedDateTime.of(date, ZoneId.of("GMT")).format(DateTimeFormatter.RFC_1123_DATE_TIME));
        }
        return this;
    }

    /**
     * Adds the EXPIRES header for the given {@link ZonedDateTime}.
     *
     * @param date The local date time (will be converted to GMT) as per {@link DateTimeFormatter#RFC_1123_DATE_TIME}
     * @return The {@link MutableHttpHeaders}
     */
    default MutableHttpHeaders expires(LocalDateTime date) {
        if (date != null) {
            add(EXPIRES, ZonedDateTime.of(date, ZoneId.of("GMT")).format(DateTimeFormatter.RFC_1123_DATE_TIME));
        }
        return this;
    }

    /**
     * Adds the LAST_MODIFIED header for the given {@link ZonedDateTime}.
     *
     * @param date The local date time (will be converted to GMT) as per {@link DateTimeFormatter#RFC_1123_DATE_TIME}
     * @return The {@link MutableHttpHeaders}
     */
    default MutableHttpHeaders lastModified(LocalDateTime date) {
        if (date != null) {
            add(LAST_MODIFIED, ZonedDateTime.of(date, ZoneId.of("GMT")).format(DateTimeFormatter.RFC_1123_DATE_TIME));
        }
        return this;
    }

    /**
     * Adds the IF_MODIFIED_SINCE header for the given {@link ZonedDateTime}.
     *
     * @param date The local date time (will be converted to GMT) as per {@link DateTimeFormatter#RFC_1123_DATE_TIME}
     * @return The {@link MutableHttpHeaders}
     */
    default MutableHttpHeaders ifModifiedSince(LocalDateTime date) {
        if (date != null) {
            add(IF_MODIFIED_SINCE, ZonedDateTime.of(date, ZoneId.of("GMT")).format(DateTimeFormatter.RFC_1123_DATE_TIME));
        }
        return this;
    }

    /**
     * Adds the DATE header for the given {@link ZonedDateTime}.
     *
     * @param timeInMillis The current time in milli seconds
     * @return The {@link MutableHttpHeaders}
     */
    default MutableHttpHeaders date(long timeInMillis) {
        ZonedDateTime date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeInMillis), ZoneId.of("GMT"));
        add(DATE, date.format(DateTimeFormatter.RFC_1123_DATE_TIME));
        return this;
    }

    /**
     * Adds the EXPIRES header for the given {@link ZonedDateTime}.
     *
     * @param timeInMillis The current time in milli seconds
     * @return The {@link MutableHttpHeaders}
     */
    default MutableHttpHeaders expires(long timeInMillis) {
        ZonedDateTime date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeInMillis), ZoneId.of("GMT"));
        add(EXPIRES, date.format(DateTimeFormatter.RFC_1123_DATE_TIME));
        return this;
    }

    /**
     * Adds the LAST_MODIFIED header for the given {@link ZonedDateTime}.
     *
     * @param timeInMillis The current time in milli seconds
     * @return The {@link MutableHttpHeaders}
     */
    default MutableHttpHeaders lastModified(long timeInMillis) {
        ZonedDateTime date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeInMillis), ZoneId.of("GMT"));
        add(LAST_MODIFIED, date.format(DateTimeFormatter.RFC_1123_DATE_TIME));
        return this;
    }

    /**
     * Adds the IF_MODIFIED_SINCE header for the given {@link ZonedDateTime}.
     *
     * @param timeInMillis The current time in milli seconds
     * @return The {@link MutableHttpHeaders}
     */
    default MutableHttpHeaders ifModifiedSince(long timeInMillis) {
        ZonedDateTime date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeInMillis), ZoneId.of("GMT"));
        add(IF_MODIFIED_SINCE, date.format(DateTimeFormatter.RFC_1123_DATE_TIME));
        return this;
    }

    /**
     * Used to configure BASIC authentication.
     *
     * @param username The username
     * @param password The password
     * @return This HTTP headers
     */
    default MutableHttpHeaders auth(String username, String password) {
        StringBuilder sb = new StringBuilder();
        sb.append(username);
        sb.append(":");
        sb.append(password);
        return auth(sb.toString());
    }

    /**
     * Used to configure BASIC authentication.
     *
     * @param userInfo The user info which is in the form "username:password"
     * @return This HTTP headers
     */
    default MutableHttpHeaders auth(String userInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append(HttpHeaderValues.AUTHORIZATION_PREFIX_BASIC);
        sb.append(" ");
        sb.append(Base64.getEncoder().encodeToString((userInfo).getBytes(StandardCharsets.ISO_8859_1)));
        String token = sb.toString();
        add(AUTHORIZATION, token);
        return this;
    }

    /**
     * Set the allowed HTTP methods.
     *
     * @param methods The methods to specify in the Allowed HTTP header
     * @return This HTTP headers
     */
    default MutableHttpHeaders allow(Collection<HttpMethod> methods) {
        String value = methods.stream().distinct().collect(Collectors.joining(","));
        return add(ALLOW, value);
    }

    /**
     * Sets the location header to the given URI.
     *
     * @param uri The URI
     * @return This HTTP headers
     */
    default MutableHttpHeaders location(URI uri) {
        return add(LOCATION, uri.toString());
    }

    /**
     * Sets the {@link HttpHeaders#CONTENT_TYPE} header to the given media type.
     *
     * @param mediaType The media type
     * @return This HTTP headers
     */
    default MutableHttpHeaders contentType(MediaType mediaType) {
        return add(CONTENT_TYPE, mediaType);
    }

    /**
     * Add a header for the given name and value.
     *
     * @param header The head name
     * @param value  The value
     * @return This headers object
     */
    default MutableHttpHeaders add(CharSequence header, ZonedDateTime value) {
        if (header != null && value != null) {
            add(header, value.format(DateTimeFormatter.RFC_1123_DATE_TIME));
        }
        return this;
    }

    /**
     * Add a header for the given name and value.
     *
     * @param header The head name
     * @param value  The value
     * @return This headers object
     */
    default MutableHttpHeaders add(CharSequence header, Integer value) {
        if (header != null && value != null) {
            return add(header, value.toString());
        }
        return this;
    }

    /**
     * Removes the header for the given name.
     *
     * @param header The header name
     */
    void remove(CharSequence header);
}
