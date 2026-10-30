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
package io.micronaut.http.context;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Utility class to work with context paths and URIs.
 * @author Sergio del Amo
 * @since 4.0.0
 */
public final class ContextPathUtils {

    private ContextPathUtils() {
    }

    public static URI prepend(URI requestURI, @Nullable ServerContextPathProvider serverContextPathProvider) throws URISyntaxException {
        return prepend(requestURI, serverContextPathProvider == null ? null : serverContextPathProvider.getContextPath());
    }

    public static URI prepend(URI requestURI, @Nullable ClientContextPathProvider clientContextPathProvider) throws URISyntaxException {
        return prepend(requestURI, clientContextPathProvider == null ? null : clientContextPathProvider.getContextPath().orElse(null));
    }

    /**
     * Prepends the context path to the request URI.
     *
     * <p>Since 5.3.0, the path of the request URI is kept as given: empty path segments, such as the one in
     * {@code /a//b}, are meaningful (RFC 3986) and are no longer merged. Only the slashes where the context path and
     * the request URI meet, and any leading slashes of the request URI, are merged, so the result never starts with
     * {@code //} and can not be read as an authority.</p>
     *
     * @param requestURI  The request URI
     * @param contextPath The context path, or {@code null}
     * @return The request URI with the context path prepended
     * @throws URISyntaxException If the resulting URI is invalid
     */
    public static URI prepend(URI requestURI, @Nullable String contextPath) throws URISyntaxException {
        if (StringUtils.isNotEmpty(contextPath)) {
            return new URI(join(contextPath, requestURI.toString()));
        }
        return requestURI;
    }

    private static String join(String contextPath, String uri) {
        if (uri.isEmpty() || "/".equals(uri)) {
            return StringUtils.prependUri(contextPath, uri);
        }
        String base = StringUtils.prependUri(contextPath, "");
        int baseEnd = base.length();
        while (baseEnd > 0 && base.charAt(baseEnd - 1) == '/') {
            baseEnd--;
        }
        StringBuilder builder = new StringBuilder(baseEnd + uri.length() + 1).append(base, 0, baseEnd);
        if (uri.charAt(0) == '?') {
            if (baseEnd == 0) {
                builder.append('/');
            }
            return builder.append(uri).toString();
        }
        int start = 0;
        while (start < uri.length() && uri.charAt(start) == '/') {
            start++;
        }
        return builder.append('/').append(uri, start, uri.length()).toString();
    }
}
