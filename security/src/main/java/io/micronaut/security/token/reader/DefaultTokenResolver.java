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

import io.micronaut.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.Optional;

/**
 * Default implementation of {@link io.micronaut.security.token.reader.TokenResolver}.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
@Singleton
public class DefaultTokenResolver implements TokenResolver {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultTokenResolver.class);

    private final Collection<TokenReader> tokenReaders;

    /**
     * Instantiates a {@link io.micronaut.security.token.reader.DefaultTokenResolver} with a list of available {@link io.micronaut.security.token.reader.TokenReader}.
     * @param tokenReaders Collection of available {@link io.micronaut.security.token.reader.TokenReader} beans.
     */
    public DefaultTokenResolver(Collection<TokenReader> tokenReaders) {
        this.tokenReaders = tokenReaders;
    }

    /**
     *
     * @param request The current HTTP request.
     * @return the first found token in the supplied request.
     */
    public Optional<String> findFirstToken(HttpRequest<?> request) {

        Optional<String> token = this.tokenReaders
                .stream()
                .map(reader -> reader.findToken(request))
                .filter(Optional::isPresent)
                .findFirst()
                .orElse(Optional.empty());

        if (LOG.isDebugEnabled()) {
            String method = request.getMethod().toString();
            String path = request.getPath();
            if (token.isPresent()) {
                LOG.debug("Token {} found in request {} {}", token.get(), method, path);
            } else {
                LOG.debug("Request {}, {}, no token found.", method, path);
            }
        }
        return token;
    }
}
