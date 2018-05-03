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

package io.micronaut.security.token.basicauth;

import io.micronaut.context.annotation.Requires;
import io.micronaut.security.token.reader.HttpHeaderTokenReader;
import io.micronaut.security.token.reader.TokenReader;

import javax.inject.Singleton;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = BasicAuthTokenReaderConfigurationProperties.PREFIX + ".enabled", notEquals = "false")
@Singleton
public class BasicAuthTokenReader extends HttpHeaderTokenReader implements TokenReader {

    /**
     * The order of the TokenReader.
     */
    public static final Integer ORDER = 0;

    protected final BasicAuthTokenReaderConfiguration bearerTokenReaderConfiguration;

    /**
     *
     * @param bearerTokenReaderConfiguration Instance of {@link BasicAuthTokenReaderConfiguration}
     */
    public BasicAuthTokenReader(BasicAuthTokenReaderConfiguration bearerTokenReaderConfiguration) {
        this.bearerTokenReaderConfiguration = bearerTokenReaderConfiguration;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    protected String getHeaderName() {
        return bearerTokenReaderConfiguration.getHeaderName();
    }

    @Override
    protected String getPrefix() {
        return bearerTokenReaderConfiguration.getPrefix();
    }
}
