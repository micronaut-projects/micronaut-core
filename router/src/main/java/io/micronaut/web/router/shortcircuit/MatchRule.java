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
package io.micronaut.web.router.shortcircuit;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * This interface represents a boolean expression that can be used to match a request. It's
 * basically an introspectable <code>{@link Predicate}<{@link HttpRequest}></code>.
 *
 * @author Jonas Konrad
 * @since 4.3.0
 */
@Experimental
@Internal
public sealed interface MatchRule extends Predicate<HttpRequest<?>> {
    /**
     * @return A rule that always returns {@code false}
     */
    static MatchRule fail() {
        return new Or(List.of());
    }

    /**
     * @return A rule that always returns {@code true}
     */
    static MatchRule pass() {
        return new And(List.of());
    }

    /**
     * @param rules The rules
     * @return A rule that matches iff all the given rules match
     */
    @NonNull
    static MatchRule and(@NonNull List<? extends MatchRule> rules) {
        if (rules.size() == 1) {
            return rules.get(0);
        } else {
            return new And(rules);
        }
    }

    /**
     * @param rules The rules
     * @return A rule that matches iff any of the given rules match
     */
    @NonNull
    static MatchRule or(@NonNull List<? extends MatchRule> rules) {
        if (rules.size() == 1) {
            return rules.get(0);
        } else {
            return new Or(rules);
        }
    }

    /**
     * A rule that matches iff all the given rules match.
     *
     * @param rules The rules
     */
    record And(@NonNull List<? extends MatchRule> rules) implements MatchRule {
        @Override
        public boolean test(HttpRequest<?> request) {
            for (MatchRule rule : rules) {
                if (!rule.test(request)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * A rule that matches iff any of the given rules match.<br>
     * Note: this should be avoided to avoid exponential formula growth during DNF conversion.
     *
     * @param rules The rules
     */
    record Or(@NonNull List<? extends MatchRule> rules) implements MatchRule {
        @Override
        public boolean test(HttpRequest<?> request) {
            for (MatchRule rule : rules) {
                if (rule.test(request)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Match the request path (not including the URI, query and anchors) exactly.
     *
     * @param path The exact path
     */
    record PathMatchExact(@NonNull String path) implements MatchRule {
        @Override
        public boolean test(HttpRequest<?> request) {
            return extractPath(request).equals(this.path);
        }

        private static String extractPath(HttpRequest<?> request) {
            String uri = request.getPath();
            if (uri == null) {
                throw new IllegalArgumentException("Argument 'uri' cannot be null");
            }
            int length = uri.length();
            if (length > 1 && uri.charAt(length - 1) == '/') {
                uri = uri.substring(0, length - 1);
            }

            //Remove any url parameters before matching
            int parameterIndex = uri.indexOf('?');
            if (parameterIndex > -1) {
                uri = uri.substring(0, parameterIndex);
            }
            if (uri.endsWith("/")) {
                uri = uri.substring(0, uri.length() - 1);
            }
            return uri;
        }
    }

    /**
     * Match the request path (not including the URI, query and anchors) using a regular expression.
     *
     * @param pattern The match pattern
     */
    record PathMatchPattern(@NonNull Pattern pattern) implements MatchRule {
        @Override
        public boolean test(HttpRequest<?> request) {
            return pattern.matcher(PathMatchExact.extractPath(request)).matches();
        }
    }

    /**
     * Match the port of the server.
     *
     * @param expectedPort The expected server port
     */
    record ServerPort(int expectedPort) implements MatchRule {
        @Override
        public boolean test(HttpRequest<?> request) {
            return request.getServerAddress().getPort() == expectedPort;
        }
    }

    /**
     * Match the {@code Content-Type} of the request.
     *
     * @param expectedType The expected content type. If this is {@code null}, the request must have no content type.
     */
    record ContentType(@Nullable MediaType expectedType) implements MatchRule {
        @Override
        public boolean test(HttpRequest<?> request) {
            return Objects.equals(request.getContentType().orElse(null), expectedType);
        }
    }

    /**
     * Match the {@code Accept} header of the request. If no accept header is present, this always
     * matches. If there is an accept header, and it contains {@code *}{@code /*}, this always
     * matches. If there is an accept header, it matches iff the {@code producedTypes} overlap with
     * the types in the header.
     *
     * @param producedTypes The potential types in the accept header
     */
    record Accept(@NonNull List<MediaType> producedTypes) implements MatchRule {
        @Override
        public boolean test(HttpRequest<?> request) {
            Collection<MediaType> accept = request.accept();
            if (accept.isEmpty()) {
                return true;
            }
            for (MediaType t : accept) {
                if (t.equals(MediaType.ALL_TYPE) || producedTypes.contains(t)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Match the request method.
     *
     * @param method The expected request method
     */
    record Method(@NonNull HttpMethod method) implements MatchRule {
        @Override
        public boolean test(HttpRequest<?> request) {
            return request.getMethod() == method;
        }
    }
}
