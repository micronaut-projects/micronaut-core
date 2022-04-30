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

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    private final UriEncoder uriEncoder;

    /**
     * Constructor to create from a URI.
     *
     * @param uri The URI
     */
    @SuppressWarnings("unchecked")
    DefaultUriBuilder(URI uri, UriEncoder uriEncoder) {
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
        this.uriEncoder = uriEncoder;
    }

    /**
     * Constructor for charsequence.
     *
     * @param uri The URI
     */
    DefaultUriBuilder(CharSequence uri, UriEncoder uriEncoder) {
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
        this.uriEncoder = uriEncoder;
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
        try {
            return new URI(uriEncoder.encode(scheme, userInfo, host, port, path.toString(), queryParams, fragment, values));
        } catch (URISyntaxException e) {
            throw new UriSyntaxException(e);
        }
    }
}
