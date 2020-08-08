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
package io.micronaut.http.util;

import edu.umd.cs.findbugs.annotations.NonNull;

import io.micronaut.http.CookieProperties;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.CookieConfiguration;
import io.micronaut.http.cookie.SameSite;
import io.micronaut.http.simple.cookies.SimpleCookie;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAmount;
import java.util.*;
import java.util.stream.Collectors;

/**
 * An utility class for parsing Set-Cookie HTTP headers into formats consumable by the framework.
 *
 * @author Jakub Antosik
 * @see CookieConfiguration
 * @see Cookie
 * @since 2.0.2
 */
public class CookieUtil {

    /**
     * Parse a Set-Cookie HTTP header into an instance of {@link Cookie}.
     *
     * @param setCookieHeader Set-Cookie HTTP header string
     * @return A {@link Cookie} with properties from the header passed
     */
    public static Cookie getCookieFromString(String setCookieHeader) {
        HeaderPropertyMapCookieConfiguration cookieConfiguration = getCookieConfigurationFromString(setCookieHeader);
        return cookieConfiguration.toCookie();
    }

    /**
     * Parse a Set-Cookie HTTP header into a {@link CookieConfiguration}.
     *
     * @param setCookieHeader Set-Cookie HTTP header string
     * @return A {@link CookieConfiguration} with properties from the header passed
     */
    public static HeaderPropertyMapCookieConfiguration getCookieConfigurationFromString(String setCookieHeader) {
        setCookieHeader = setCookieHeader.trim();
        if (!setCookieHeader.toLowerCase().startsWith(HttpHeaders.SET_COOKIE.toLowerCase())) {
            throw new IllegalArgumentException("Not a Set-Cookie HTTP header");
        }

        String[] semicolonParts = setCookieHeader.split(";");
        String[] cookieDefinition = semicolonParts[0].split("=", 2);

        Map<String, String> props = Arrays.stream(semicolonParts).skip(1)
                .map(propertyPart -> {
                    String[] split = propertyPart.split("=", 2);
                    //lenient parsing of empty segments caused by headers like "Set-Cookie: test=1;; Secure;"
                    //(multiple semicolons)
                    if (split[0].trim().equals("")) {
                        return null;
                    }
                    if (split.length < 2) {
                        return new AbstractMap.SimpleEntry<>(split[0].trim().toLowerCase(), "");
                    }
                    return new AbstractMap.SimpleEntry<>(split[0].trim().toLowerCase(), trimQuotes(split[1].trim()));
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

        String cookieName = cookieDefinition[0].substring((HttpHeaders.SET_COOKIE + ":").length()).trim();
        String cookieValue = trimQuotes(cookieDefinition[1]);
        return new HeaderPropertyMapCookieConfiguration(cookieName, cookieValue, props);
    }

    private static String trimQuotes(String propertyValue) {
        if (propertyValue == null) {
            return null;
        }
        return propertyValue.startsWith("\"") && propertyValue.endsWith("\"")
                ? propertyValue.substring(1, propertyValue.length() - 1)
                : propertyValue;
    }


    private static class HeaderPropertyMapCookieConfiguration implements CookieConfiguration {

        private static final DateTimeFormatter ANSI_C_ASCTIME_FORMATTER = new DateTimeFormatterBuilder()
                .appendPattern("[EEEE][E][,] MMM [ ]")
                .appendValueReduced(ChronoField.DAY_OF_MONTH, 1, 2, 1)
                .appendPattern(" HH:mm:ss ")
                .appendValueReduced(ChronoField.YEAR_OF_ERA, 2, 4, LocalDate.now().minusYears(80))
                .toFormatter();

        private static final DateTimeFormatter RFC_5322_RFC_850_FORMATTER = new DateTimeFormatterBuilder()
                .appendPattern("[EEEE][E], dd[-][ ]MMM[-][ ]")
                .appendValueReduced(ChronoField.YEAR_OF_ERA, 2, 4, LocalDate.now().minusYears(80))
                .appendPattern(" HH:mm:ss ")
                .appendZoneOrOffsetId()
                .toFormatter();

        private final String cookieName;
        private final String cookieValue;
        private final Map<String, String> props;

        public HeaderPropertyMapCookieConfiguration(String cookieName, String cookieValue, Map<String, String> props) {
            this.cookieName = cookieName;
            this.cookieValue = cookieValue;
            this.props = props;
        }

        public Cookie toCookie() {
            return new SimpleCookie(cookieName, cookieValue)
                    .configure(this, isCookieSecure().orElse(false));
        }

        @Override
        @NonNull
        public String getCookieName() {
            return cookieName;
        }

        @Override
        public Optional<String> getCookieDomain() {
            return Optional.ofNullable(props.get(CookieProperties.DOMAIN.toLowerCase()));
        }

        @Override
        public Optional<String> getCookiePath() {
            return Optional.ofNullable(props.get(CookieProperties.PATH.toLowerCase()));
        }

        @Override
        public Optional<Boolean> isCookieHttpOnly() {
            return Optional.of(props.containsKey(CookieProperties.HTTP_ONLY.toLowerCase()));
        }

        public Optional<Boolean> isCookieSecure() {
            return Optional.of(props.containsKey(CookieProperties.SECURE.toLowerCase()));
        }

        @Override
        public Optional<TemporalAmount> getCookieMaxAge() {
            // https://tools.ietf.org/html/rfc6265#page-19 - Max-Age has precedence over Expires.
            if (props.get(CookieProperties.MAX_AGE.toLowerCase()) != null) {
                String propertyValue = props.get(CookieProperties.MAX_AGE.toLowerCase());
                long maxAge = propertyValue.isEmpty() ? 0 : Long.parseLong(propertyValue);
                return Optional.ofNullable(Duration.ofSeconds(maxAge));
            } else if (props.get(CookieProperties.EXPIRES.toLowerCase()) != null) {
                Optional<LocalDateTime> expiry = parseCookieDate(props.get(CookieProperties.EXPIRES.toLowerCase()));
                return expiry.map($ -> Duration.between($, LocalDateTime.now()));
            } else {
                return Optional.empty();
            }
        }

        private Optional<LocalDateTime> parseCookieDate(String propertyValue) {

            Optional<LocalDateTime> localDateTime = Arrays.stream(new DateTimeFormatter[]{
                    DateTimeFormatter.RFC_1123_DATE_TIME,
                    RFC_5322_RFC_850_FORMATTER,
                    ANSI_C_ASCTIME_FORMATTER
            }).map(dateTimeFormatter -> {
                try {
                    return LocalDateTime.parse(propertyValue, dateTimeFormatter);
                } catch (DateTimeParseException e) {
                    //ignored, try other patterns
                    return null;
                }
            }).filter(Objects::nonNull).findFirst();

            return localDateTime;
        }

        @Override
        public Optional<SameSite> getCookieSameSite() {
            String attributeValue = props.get(CookieProperties.SAME_SITE.toLowerCase());
            if (attributeValue == null) {
                return Optional.empty();
            }

            SameSite value = null;
            try {
                value = SameSite.valueOf(attributeValue);
            } catch (IllegalArgumentException e) {
                //ignore unparseable value
            }
            return Optional.ofNullable(value);
        }
    }
}
