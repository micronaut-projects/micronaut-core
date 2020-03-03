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
package io.micronaut.http.uri;

import io.micronaut.core.convert.value.MutableConvertibleMultiValues;
import io.micronaut.core.convert.value.MutableConvertibleMultiValuesMap;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.exceptions.UriSyntaxException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
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

    private String authority;
    private final MutableConvertibleMultiValues<String> queryParams;
    private String scheme;
    private String userInfo;
    private String host;
    private int port = -1;
    private StringBuilder path = new StringBuilder();
    private String fragment;

    /**
     * Constructor to create from a URI.
     * @param uri The URI
     */
    @SuppressWarnings("unchecked")
    DefaultUriBuilder(URI uri) {
        this.scheme = uri.getScheme();
        this.userInfo = uri.getRawUserInfo();
        this.authority = uri.getRawAuthority();
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
            this.queryParams = new MutableConvertibleMultiValuesMap<String>(parameters);
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
                    final Map parameters = new QueryStringDecoder(query).parameters();
                    this.queryParams = new MutableConvertibleMultiValuesMap<String>(parameters);
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
                    final Map parameters = new QueryStringDecoder(uri.toString()).parameters();
                    this.queryParams = new MutableConvertibleMultiValuesMap<String>(parameters);
                } else {
                    this.queryParams = new MutableConvertibleMultiValuesMap<>();
                }

            } else {
                this.path = new StringBuilder(uri.toString());
                this.queryParams = new MutableConvertibleMultiValuesMap<>();
            }
        }
    }

    @Nonnull
    @Override
    public UriBuilder fragment(@Nullable String fragment) {
        if (fragment != null) {
            this.fragment = fragment;
        }
        return this;
    }

    @Nonnull
    @Override
    public UriBuilder scheme(@Nullable String scheme) {
        if (scheme != null) {
            this.scheme = scheme;
        }
        return this;
    }

    @Nonnull
    @Override
    public UriBuilder userInfo(@Nullable String userInfo) {
        if (userInfo != null) {
            this.userInfo = userInfo;
        }
        return this;
    }

    @Nonnull
    @Override
    public UriBuilder host(@Nullable String host) {
        if (host != null) {
            this.host = host;
        }
        return this;
    }

    @Nonnull
    @Override
    public UriBuilder port(int port) {
        if (port < -1) {
            throw new IllegalArgumentException("Invalid port value");
        }
        this.port = port;
        return this;
    }

    @Nonnull
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

    @Nonnull
    @Override
    public UriBuilder replacePath(@Nullable String path) {
        if (path != null) {
            this.path.setLength(0);
            this.path.append(path);
        }
        return this;
    }

    @Nonnull
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

    @Nonnull
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

    @Nonnull
    @Override
    public URI build() {
        try {
            return new URI(reconstructAsString(null));
        } catch (URISyntaxException e) {
            throw new UriSyntaxException(e);
        }
    }

    @Nonnull
    @Override
    public URI expand(Map<String, ? super Object> values) {
        String uri = reconstructAsString(values);
        return URI.create(uri);
    }

    @Override
    public String toString() {
        return build().toString();
    }

    private String reconstructAsString(Map<String, ? super Object> values) {
        StringBuilder builder = new StringBuilder();
        String scheme = this.scheme;
        String host = this.host;
        if (StringUtils.isNotEmpty(scheme)) {
            if (isTemplate(scheme, values)) {
                scheme = UriTemplate.of(scheme).expand(values);
            }
            builder.append(scheme)
                   .append(":");
        }

        final boolean hasPort = port != -1;
        final boolean hasHost = host != null;
        final boolean hasUserInfo = StringUtils.isNotEmpty(userInfo);
        if (hasUserInfo || hasHost || hasPort) {
            builder.append("//");
            if (hasUserInfo) {
                String userInfo = this.userInfo;
                if (userInfo.contains(":")) {
                    final String[] sa = userInfo.split(":");
                    userInfo = expandOrEncode(sa[0], values) + ":" + expandOrEncode(sa[1], values);
                } else {
                    userInfo = expandOrEncode(userInfo, values);
                }
                builder.append(userInfo);
                builder.append("@");
            }

            if (hasHost) {
                host = expandOrEncode(host, values);
                builder.append(host);
            }

            if (hasPort) {
                builder.append(":").append(port);
            }
        } else {
            String authority = this.authority;
            if (StringUtils.isNotEmpty(authority)) {
                authority = expandOrEncode(authority, values);
                builder.append("//")
                       .append(authority);
            }
        }

        StringBuilder path = this.path;
        if (StringUtils.isNotEmpty(path)) {
            if (builder.length() > 0 && path.charAt(0) != '/') {
                builder.append('/');
            }
            String pathStr = path.toString();
            if (isTemplate(pathStr, values)) {
                pathStr = UriTemplate.of(pathStr).expand(values);
            }

            builder.append(pathStr);
        }

        if (!queryParams.isEmpty()) {
            builder.append('?');
            builder.append(buildQueryParams(values));
        }

        String fragment = this.fragment;
        if (StringUtils.isNotEmpty(fragment)) {
            fragment = expandOrEncode(fragment, values);
            if (fragment.charAt(0) != '#') {
                builder.append('#');
            }
            builder.append(fragment);
        }
        return builder.toString();
    }

    private boolean isTemplate(String value, Map<String, ? super Object> values) {
        return values != null && value.indexOf('{') > -1;
    }

    private String buildQueryParams(Map<String, ? super Object> values) {
        if (!queryParams.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            final Iterator<String> nameIterator = queryParams.names().iterator();
            while (nameIterator.hasNext()) {
                String rawName = nameIterator.next();
                String name = expandOrEncode(rawName, values);

                final Iterator<String> i = queryParams.getAll(rawName).iterator();
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

    private String expandOrEncode(String value, Map<String, ? super Object> values) {
        if (isTemplate(value, values)) {
            value = UriTemplate.of(value).expand(values);
        } else {
            value = encode(value);
        }
        return value;
    }

    private String encode(String userInfo) {
        try {
            return URLEncoder.encode(userInfo, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("No available charset: " + e.getMessage());
        }
    }
}
