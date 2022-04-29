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
                    this.port = Integer.valueOf(port);
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
        String scheme = this.scheme;
        if (StringUtils.isNotEmpty(scheme) && isTemplate(scheme, values)) {
            scheme = UriTemplate.of(scheme).expand(values);
        }

        String userInfo = this.userInfo;
        if (StringUtils.isNotEmpty(userInfo)) {
            if (userInfo.contains(":")) {
                final String[] sa = userInfo.split(":");
                userInfo = expandOrEncode(sa[0], values) + ":" + expandOrEncode(sa[1], values);
            } else {
                userInfo = expandOrEncode(userInfo, values);
            }
        }

        String host = this.host == null ? null : expandOrEncode(this.host, values);

        String path = this.path.toString();
        if (StringUtils.isNotEmpty(path) && isTemplate(path, values)) {
            path = UriTemplate.of(path).expand(values);
        }

        String queryParams = this.queryParams.isEmpty() ? null : buildQueryParams(values);

        String fragment = StringUtils.isEmpty(this.fragment) ? this.fragment : expandOrEncode(this.fragment, values);

        StringBuilder builder = new StringBuilder();
        if (StringUtils.isNotEmpty(scheme)) {
            builder.append(scheme).append("://");
        }
        if (StringUtils.isNotEmpty(userInfo)) {
            builder.append(userInfo).append('@');
        }
        if (StringUtils.isNotEmpty(host)) {
            builder.append(host);
        }
        if (port != -1) {
            builder.append(':').append(port);
        }
        if (StringUtils.isNotEmpty(path)) {
            if (builder.length() > 0 && path.charAt(0) != '/') {
                builder.append('/');
            }
            builder.append(encodePath(path));
        }
        if (StringUtils.isNotEmpty(queryParams)) {
            builder.append('?').append(queryParams);
        }
        if (StringUtils.isNotEmpty(fragment)) {
            if (fragment.charAt(0) != '#') {
                builder.append('#');
            }
            builder.append(fragment);
        }

        try {
            return new URI(builder.toString());
        } catch (URISyntaxException e) {
            throw new UriSyntaxException(e);
        }
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
                String name = expandOrEncode(rawName, values);

                final Iterator<String> i = entry.getValue().iterator();
                while (i.hasNext()) {
                    String v = expandOrEncode(i.next(), values);
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

    private boolean isValidPathChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '-' || c == '.' || c == '_' || c == '~'
                || c == '!' || c == '$' || c == '&' || c == '\'' || c == '(' || c == ')'
                || c == '*' || c == '+' || c == ',' || c == ';' || c == '=' || c == ':' || c == '@' || c == '/';
    }

    private String encodePath(String path) {
        StringBuilder builder = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (isValidPathChar(c)) {
                builder.append(c);
            } else if (c == '%') {
                if (i + 2 >= path.length()) {
                    throw new IllegalArgumentException("Invalid URI percent-encoding");
                }
                builder.append('%').append(i + 1).append(i + 2);
                i += 2;
            } else if (c == ' ') {
                builder.append("%20");
            } else {
                //TODO This does percent-encoding. Make this more efficient as URLEncoder.encode is not intended to be called char-by-char
                try {
                    builder.append(URLEncoder.encode(String.valueOf(c), StandardCharsets.UTF_8.toString()));
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalStateException("No charset found: " + e.getMessage());
                }
            }
        }
        return builder.toString();
    }


    private String expandOrEncode(String value, Map<String, ? super Object> values) {
        if (isTemplate(value, values)) {
            value = UriTemplate.of(value).expand(values);
        } else {
            try {
                value = URLEncoder.encode(value, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException("No available charset: " + e.getMessage());
            }
        }
        return value;
    }
}
