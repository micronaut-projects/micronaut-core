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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
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

    @NonNull
    public static URI prepend(@NonNull URI requestURI, @Nullable ServerContextPathProvider serverContextPathProvider) throws URISyntaxException {
        return prepend(requestURI, serverContextPathProvider.getContextPath());
    }

    @NonNull
    public static URI prepend(@NonNull URI requestURI, @Nullable ClientContextPathProvider clientContextPathProvider) throws URISyntaxException {
        return prepend(requestURI, clientContextPathProvider.getContextPath().orElse(null));
    }

    @NonNull
    public static URI prepend(@NonNull URI requestURI, @Nullable String contextPath) throws URISyntaxException {
        if (StringUtils.isNotEmpty(contextPath)) {
            return new URI(StringUtils.prependUri(contextPath, requestURI.toString()));
        }
        return requestURI;
    }
}
