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

package io.micronaut.security.token.jwt.bearer;

import io.micronaut.context.annotation.Requires;
import io.micronaut.security.token.jwt.cookie.JwtCookieTokenReader;
import io.micronaut.security.token.reader.HttpHeaderTokenReader;
import io.micronaut.security.token.reader.TokenReader;

import javax.inject.Singleton;

/**
 * Reads JWT token from {@link io.micronaut.http.HttpHeaders#AUTHORIZATION} header. e.g. Bearer XXXXX.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = BearerTokenConfigurationProperties.PREFIX + ".enabled", notEquals = "false")
@Singleton
public class BearerTokenReader extends HttpHeaderTokenReader implements TokenReader {

    /*
     *
     * The order of the TokenReader.
     */
    public static final Integer ORDER = JwtCookieTokenReader.ORDER - 100;

    protected final BearerTokenConfiguration bearerTokenConfiguration;

    /**
     *
     * @param bearerTokenConfiguration Instance of {@link BearerTokenConfiguration}
     */
    public BearerTokenReader(BearerTokenConfiguration bearerTokenConfiguration) {
        this.bearerTokenConfiguration = bearerTokenConfiguration;
    }

    @Override
    protected String getHeaderName() {
        return bearerTokenConfiguration.getHeaderName();
    }

    @Override
    protected String getPrefix() {
        return bearerTokenConfiguration.getPrefix();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
