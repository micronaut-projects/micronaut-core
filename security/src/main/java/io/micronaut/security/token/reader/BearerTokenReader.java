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

package io.micronaut.security.token.reader;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Singleton;
import java.util.Optional;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = BearerTokenReaderConfigurationProperties.PREFIX + ".enabled", notEquals = "false")
@Singleton
public class BearerTokenReader implements TokenReader {

    private static final Logger LOG = LoggerFactory.getLogger(BearerTokenReader.class);

    protected final BearerTokenReaderConfiguration bearerTokenReaderConfiguration;

    /**
     *
     * @param bearerTokenReaderConfiguration Instance of {@link BearerTokenReaderConfiguration}
     */
    public BearerTokenReader(BearerTokenReaderConfiguration bearerTokenReaderConfiguration) {
        this.bearerTokenReaderConfiguration = bearerTokenReaderConfiguration;
    }

    /**
     * Search for a JWT token in a HTTP request.
     * @param request The request to look for the token in
     * @return if the JWT token is found it is returned, empty if not
     */
    @Override
    public Optional<String> findToken(HttpRequest<?> request) {
        LOG.debug("Looking for bearer token in Authorization header");
        HttpHeaders headers = request.getHeaders();
        Optional<String> authorizationHeader = headers.findFirst(bearerTokenReaderConfiguration.getHeaderName());
        return authorizationHeader.flatMap(this::extractTokenFromAuthorization);
    }

    /**
     *
     * @param authorization Authorization header value
     * @return If prefix is 'Bearer' for 'Bearer XXX' it returns 'XXX'
     */
    protected Optional<String> extractTokenFromAuthorization(String authorization) {
        StringBuilder sb = new StringBuilder();
        final String prefix = bearerTokenReaderConfiguration.getPrefix();
        if ( prefix != null && !prefix.isEmpty() ) {
            sb.append(prefix);
            sb.append(" ");
        }
        String str = sb.toString();
        if ( authorization.startsWith(str) ) {
            return Optional.of(authorization.substring(str.length(), authorization.length()));
        } else {
            LOG.debug("{} does not start with {}", authorization, str);
            return Optional.empty();
        }
    }
}
