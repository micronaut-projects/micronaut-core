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
package io.micronaut.core.bind;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.http.HttpMessage;
import io.micronaut.http.HttpRequest;

/**
 * <p>Common interface for HTTP request implementations.</p>
 *
 * @param <B> The Http message body
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("MethodName")
public interface TestHttpRequest<B> extends HttpMessage<B>, HttpRequest<B> {

    /**
     * Constant for HTTP scheme.
     */
    String SCHEME_HTTP = "http";

    /**
     * Constant for HTTPS scheme.
     */
    String SCHEME_HTTPS = "https";

    /**
     * @return The {@link ATest} instance
     */
    @NonNull
    ConvertibleValues<String> getStringValue();


}
