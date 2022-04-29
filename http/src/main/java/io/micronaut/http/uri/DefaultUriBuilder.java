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
package io.micronaut.http.uri;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.value.MutableConvertibleMultiValues;
import io.micronaut.core.convert.value.MutableConvertibleMultiValuesMap;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.exceptions.UriSyntaxException;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static io.micronaut.http.uri.UriTemplate.PATTERN_FULL_PATH;
import static io.micronaut.http.uri.UriTemplate.PATTERN_FULL_URI;

/**
 * Default implementation of the {@link UriBuilder} interface.
 *
 * @author graemerocher
 * @since 1.0.2
 */
class DefaultUriBuilder implements UriBuilder {

    private final MutableConvertibleMultiValues<String> queryParams;
    private String scheme;
    private String userInfo;
    private String host;
    private int port = -1;
    private StringBuilder path = new StringBuilder();
    private String fragment;

    /**
     * Constructor to create from a URI.
     *
     * @param uri The URI
     */
    @SuppressWarnings("unchecked")
    DefaultUriBuilder(URI uri) {
        this.scheme = uri.getScheme();
        this.userInfo = uri.getRawUserInfo();
        this.host = uri.getHost();
        this.port = uri.getPort();
        this.path = new StringBuilder();
        final String rawPath = uri.getRawPath();
        if (rawPath != null) {
            this.path.append(rawPath);
        }
        this.fragment = uri.getRawFragment();
        final String query = uri.getQuery();
        if (query != null) {
            final Map parameters = new QueryStringDecoder(uri).parameters();
            this.queryParams = new MutableConvertibleMultiValuesMap<>(parameters);
        } else {
            this.queryParams = new MutableConvertibleMultiValuesMap<>();
        }
    }

    /**
     * Constructor for charsequence.
     *
     * @param uri The URI
     */
    DefaultUriBuilder(CharSequence uri) {
        if (UriTemplate.PATTERN_SCHEME.matcher(uri).matches()) {
            Matcher matcher = PATTERN_FULL_URI.matcher(uri);

            if (matcher.find()) {
                String scheme = matcher.group(2);
                if (scheme != null) {
                    this.scheme = scheme;
                }
                String userInfo = matcher.group(5);
                String host = matcher.group(6);
                String port = matcher.group(8);
                String path = matcher.group(9);
                String query = matcher.group(11);
                String fragment = matcher.group(13);
                if (userInfo != null) {
                    this.userInfo = userInfo;
                }
                if (host != null) {
                    this.host = host;
                }
                if (port != null) {
                    this.port = Integer.parseInt(port);
                }
                if (path != null) {
                    if (fragment != null) {
                        this.fragment = fragment;
                    }
                    this.path = new StringBuilder(path);
                }
                if (query != null) {
                    final Map parameters = new QueryStringDecoder(query, StandardCharsets.UTF_8, false).parameters();
                    this.queryParams = new MutableConvertibleMultiValuesMap<>(parameters);
                } else {
                    this.queryParams = new MutableConvertibleMultiValuesMap<>();
                }
            } else {
                this.path = new StringBuilder(uri.toString());
                this.queryParams = new MutableConvertibleMultiValuesMap<>();
            }
        } else {
            Matcher matcher = PATTERN_FULL_PATH.matcher(uri);
            if (matcher.find()) {
                final String path = matcher.group(1);
                final String query = matcher.group(3);
                this.fragment = matcher.group(5);

                this.path = new StringBuilder(path);
                if (query != null) {
                    final Map parameters = new QueryStringDecoder(query, StandardCharsets.UTF_8, false).parameters();
                    this.queryParams = new MutableConvertibleMultiValuesMap<>(parameters);
                } else {
                    this.queryParams = new MutableConvertibleMultiValuesMap<>();
                }

            } else {
                this.path = new StringBuilder(uri.toString());
                this.queryParams = new MutableConvertibleMultiValuesMap<>();
            }
        }
    }

    @NonNull
    @Override
    public UriBuilder fragment(@Nullable String fragment) {
        if (fragment != null) {
            this.fragment = fragment;
        }
        return this;
    }

    @NonNull
    @Override
    public UriBuilder scheme(@Nullable String scheme) {
        if (scheme != null) {
            this.scheme = scheme;
        }
        return this;
    }

    @NonNull
    @Override
    public UriBuilder userInfo(@Nullable String userInfo) {
        if (userInfo != null) {
            this.userInfo = userInfo;
        }
        return this;
    }

    @NonNull
    @Override
    public UriBuilder host(@Nullable String host) {
        if (host != null) {
            this.host = host;
        }
        return this;
    }

    @NonNull
    @Override
    public UriBuilder port(int port) {
        if (port < -1) {
            throw new IllegalArgumentException("Invalid port value");
        }
        this.port = port;
        return this;
    }

    @NonNull
    @Override
    public UriBuilder path(@Nullable String path) {
        if (StringUtils.isNotEmpty(path)) {
            final int len = this.path.length();
            final boolean endsWithSlash = len > 0 && this.path.charAt(len - 1) == '/';
            if (endsWithSlash) {
                if (path.charAt(0) == '/') {
                    this.path.append(path.substring(1));
                } else {
                    this.path.append(path);
                }
            } else {
                if (path.charAt(0) == '/') {
                    this.path.append(path);
                } else {
                    this.path.append('/').append(path);
                }
            }
        }
        return this;
    }

    @NonNull
    @Override
    public UriBuilder replacePath(@Nullable String path) {
        if (path != null) {
            this.path.setLength(0);
            this.path.append(path);
        }
        return this;
    }

    @NonNull
    @Override
    public UriBuilder queryParam(String name, Object... values) {
        if (StringUtils.isNotEmpty(name) && ArrayUtils.isNotEmpty(values)) {
            final List<String> existing = queryParams.getAll(name);
            List<String> strings = existing != null ? new ArrayList<>(existing) : new ArrayList<>(values.length);
            for (Object value : values) {
                if (value != null) {
                    strings.add(value.toString());
                }
            }
            queryParams.put(name, strings);
        }
        return this;
    }

    @NonNull
    @Override
    public UriBuilder replaceQueryParam(String name, Object... values) {
        if (StringUtils.isNotEmpty(name) && ArrayUtils.isNotEmpty(values)) {
            List<String> strings = new ArrayList<>(values.length);
            for (Object value : values) {
                if (value != null) {
                    strings.add(value.toString());
                }
            }
            queryParams.put(name, strings);
        }
        return this;
    }

    @NonNull
    @Override
    public URI build() {
        return constructUri(null);
    }

    @NonNull
    @Override
    public URI expand(Map<String, ? super Object> values) {
        return constructUri(values);
    }

    @Override
    public String toString() {
        return build().toString();
    }

    private URI constructUri(Map<String, ? super Object> values) {
        String scheme = isTemplate(this.scheme, values) ? UriTemplate.of(this.scheme).expand(values) : this.scheme;
        String userInfo = expandOrEncodeUserInfo(this.userInfo, values);
        String host = expandOrEncodeForm(this.host, values);
        String path = expandOrEncodePath(this.path.toString(), values);
        String queryParams = buildQueryParams(values);
        String fragment = expandOrEncodeFragment(this.fragment, values);
        try {
            return new URI(buildUriString(scheme, userInfo, host, path, queryParams, fragment));
        } catch (URISyntaxException e) {
            throw new UriSyntaxException(e);
        }
    }

    private String buildUriString(String scheme, String userInfo, String host, String path, String queryParams, String fragment) {
        /*
         * The is no one correct way to construct a URI. We drop elements
         * from the URI when they would be invalid such as userInfo without
         * an authority. And we delimit the tokens by taking into account several
         * specs and what's most compatible between them:
         *   https://datatracker.ietf.org/doc/html/rfc1738#section-3.3
         *   https://datatracker.ietf.org/doc/html/rfc2396#section-3
         *   https://datatracker.ietf.org/doc/html/rfc3986#section-3
         */
        StringBuilder uriText = new StringBuilder();
        if (StringUtils.isNotEmpty(scheme)) {
            uriText.append(scheme).append(":");
        }
        if (StringUtils.isNotEmpty(host)) {
            if (StringUtils.isNotEmpty(userInfo)) {
                uriText.append(userInfo).append('@');
            }
            uriText.append("//").append(host);

            if (port != -1) {
                uriText.append(':').append(port);
            }
        }
        if (StringUtils.isEmpty(path)) {
            uriText.append('/');
        } else {
            if (uriText.length() > 0 && path.charAt(0) != '/') {
                uriText.append('/');
            }
            uriText.append(path);
        }
        if (StringUtils.isNotEmpty(queryParams)) {
            uriText.append('?').append(queryParams);
        }
        if (StringUtils.isNotEmpty(fragment)) {
            if (fragment.charAt(0) != '#') {
                uriText.append('#');
            }
            uriText.append(fragment);
        }
        return uriText.toString();
    }

    private boolean isTemplate(String value, Map<String, ? super Object> values) {
        return values != null && value.indexOf('{') > -1;
    }

    private String buildQueryParams(Map<String, ? super Object> values) {
        if (!queryParams.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            final Iterator<Map.Entry<String, List<String>>> nameIterator = queryParams.iterator();
            while (nameIterator.hasNext()) {
                Map.Entry<String, List<String>> entry = nameIterator.next();
                String rawName = entry.getKey();
                String name = expandOrEncodeForm(rawName, values);

                final Iterator<String> i = entry.getValue().iterator();
                while (i.hasNext()) {
                    String v = expandOrEncodeForm(i.next(), values);
                    builder.append(name).append('=').append(v);
                    if (i.hasNext()) {
                        builder.append('&');
                    }
                }
                if (nameIterator.hasNext()) {
                    builder.append('&');
                }

            }
            return builder.toString();
        }
        return null;
    }

    private String expandOrEncodeUserInfo(String userInfo, Map<String, ? super Object> values) {
        if (StringUtils.isNotEmpty(userInfo)) {
            if (userInfo.contains(":")) {
                final String[] sa = userInfo.split(":");
                userInfo = expandOrEncodeForm(sa[0], values) + ":" + expandOrEncodeForm(sa[1], values);
            } else {
                userInfo = expandOrEncodeForm(userInfo, values);
            }
        }
        return userInfo;
    }

    private String expandOrEncodeForm(String value, Map<String, ? super Object> values) {
        try {
            if (StringUtils.isEmpty(value)) {
                return null;
            }
            return isTemplate(value, values) ? UriTemplate.of(value).expand(values) : URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("No available charset: " + e.getMessage());
        }
    }

    private String expandOrEncodePath(String path, Map<String, ? super Object> values) {
        return isTemplate(path, values) ? UriTemplate.of(path).expand(values) : encode(path, false);
    }

    private String expandOrEncodeFragment(String fragment, Map<String, ? super Object> values) {
        return isTemplate(fragment, values) ? UriTemplate.of(fragment).expand(values) : encode(fragment, true);
    }

    private boolean isAllowed(char c) {
        /* See:
         *   "Path" -- https://datatracker.ietf.org/doc/html/rfc3986#section-3.3
         *   "Characters" -- https://datatracker.ietf.org/doc/html/rfc3986#section-2
         */
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') // alpha-numeric unreserved chars
                || c == '-' || c == '.' || c == '_' || c == '~' // other unreserved chars
                || c == '!' || c == '$' || c == '&' || c == '\'' || c == '(' || c == ')' // sub-delim chars
                || c == '*' || c == '+' || c == ',' || c == ';' || c == '=' // more sub-delim chars
                || c == ':' || c == '@' //other pchars
                || c == '/'; //allow slashes too since a whole path or URI can be tested, not just segments
    }

    private String encode(String pathOrFragment, boolean allowQuestionMark) {
        final int pathLen = pathOrFragment.length();
        StringBuilder uriPath = new StringBuilder(pathLen);
        for (int i = 0; i < pathLen; i++) {
            char c = pathOrFragment.charAt(i);
            if (isAllowed(c)) {
                uriPath.append(c);
            } else if (c == '%') {
                if (i + 2 >= pathOrFragment.length()) {
                    char c1 = pathOrFragment.charAt(i + 1);
                    char c2 = pathOrFragment.charAt(i + 2);
                    if (c1 >= '0' && c2 >= '0' && c1 <= '9' && c2 <= '9') {
                        uriPath.append("%").append(c1).append(c2);
                        i += 2;
                    } else {
                        uriPath.append("%25");
                    }
                } else {
                    uriPath.append("%25");
                }
            } else if (c == ' ') {
                uriPath.append("%20");
            } else if (allowQuestionMark && c == '?') {
                uriPath.append('?');
            } else {
                //TODO This does percent-encoding. Make this more efficient as URLEncoder.encode is not intended to be called char-by-char
                try {
                    uriPath.append(URLEncoder.encode(String.valueOf(c), StandardCharsets.UTF_8.toString()));
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalStateException("No charset found: " + e.getMessage());
                }
            }
        }
        return uriPath.toString();
    }
}
