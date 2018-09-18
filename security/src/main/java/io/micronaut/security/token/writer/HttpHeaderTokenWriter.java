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

package io.micronaut.security.token.writer;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MutableHttpRequest;

import javax.inject.Singleton;

/**
 *  Write the token in an HTTP header.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
@Requires(property = HttpHeaderTokenWriterConfigurationProperties.PREFIX + ".enabled")
@Requires(beans = {HttpHeaderTokenWriterConfiguration.class})
public class HttpHeaderTokenWriter implements TokenWriter {
    protected final HttpHeaderTokenWriterConfiguration httpHeaderTokenWriterConfiguration;

    /**
     *
     * @param httpHeaderTokenWriterConfiguration The {@link HttpHeaderTokenWriter} configuration
     */
    public HttpHeaderTokenWriter(HttpHeaderTokenWriterConfiguration httpHeaderTokenWriterConfiguration) {
        this.httpHeaderTokenWriterConfiguration = httpHeaderTokenWriterConfiguration;
    }

    /**
     *
     * @return the HTTP Header name where the token will be written to
     */
    protected String getHeaderName() {
        return httpHeaderTokenWriterConfiguration.getHeaderName();
    }

    /**
     * Writes the token to the request.
     * @param request The {@link MutableHttpRequest} instance
     * @param token A token ( e.g. JWT token, basic auth token...)
     */
    @Override
    public void writeToken(MutableHttpRequest<?> request, String token) {
        request.header(getHeaderName(), headerValue(token));
    }

    /**
     * @param token the token being written
     * @return the value which will be written to an HTTP Header
     */
    protected String headerValue(String token) {
        StringBuilder sb = new StringBuilder();
        if (httpHeaderTokenWriterConfiguration.getPrefix() != null) {
            sb.append(httpHeaderTokenWriterConfiguration.getPrefix());
            if (!httpHeaderTokenWriterConfiguration.getPrefix().endsWith(" ")) {
                sb.append(" ");
            }
        }
        sb.append(token);
        return sb.toString();
    }
}
