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

package io.micronaut.security.token.generator;

import io.micronaut.context.BeanContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.security.authentication.AuthenticationSuccess;
import io.micronaut.security.token.configuration.TokenConfiguration;
import io.micronaut.security.token.configuration.TokenEncryptionConfiguration;
import io.micronaut.security.token.render.AccessRefreshToken;
import io.micronaut.security.token.render.TokenRenderer;
import org.pac4j.core.profile.jwt.JwtClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Singleton;
import java.util.Date;
import java.util.Map;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class DefaultAccessRefreshTokenGenerator implements AccessRefreshTokenGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAccessRefreshTokenGenerator.class);
    private static final int MILLISECONDS_IN_A_SECOND = 1000;

    protected final TokenConfiguration tokenConfiguration;
    protected final TokenEncryptionConfiguration tokenEncryptionConfiguration;
    protected final BeanContext beanContext;
    protected final TokenRenderer tokenRenderer;
    protected final TokenGenerator tokenGenerator;

    /**
     *
     * @param beanContext Instance of {@link BeanContext}
     * @param tokenConfiguration Instance of {@link TokenConfiguration}
     * @param tokenEncryptionConfiguration Instance of {@link TokenEncryptionConfiguration}
     * @param tokenRenderer Instance of {@link TokenRenderer}
     * @param tokenGenerator Intance of {@link TokenGenerator}
     */
    public DefaultAccessRefreshTokenGenerator(BeanContext beanContext,
                                              TokenConfiguration tokenConfiguration,
                                              TokenEncryptionConfiguration tokenEncryptionConfiguration,
                                              TokenRenderer tokenRenderer,
                                              TokenGenerator tokenGenerator) {
        this.beanContext = beanContext;
        this.tokenConfiguration = tokenConfiguration;
        this.tokenEncryptionConfiguration = tokenEncryptionConfiguration;
        this.tokenRenderer = tokenRenderer;
        this.tokenGenerator = tokenGenerator;
    }

    /**
     * Wraps {@link AccessRefreshToken} in an HTTP Response.
     * @param accessRefreshToken {@link AccessRefreshToken}
     * @return HTTPResponse warpping {@link AccessRefreshToken}
     */
    protected HttpResponse<AccessRefreshToken> httpResponseWithAccessRefreshToken(AccessRefreshToken accessRefreshToken) {
        return HttpResponse.ok(accessRefreshToken);
    }

    /**
     *
     * @param userDetails Authenticated user's representation.
     * @return
     */
    @Override
    public HttpResponse<AccessRefreshToken> generate(AuthenticationSuccess userDetails) {
        try {
            String accessToken = tokenGenerator.generateToken(userDetails, tokenConfiguration.getDefaultExpiration());
            String refreshToken = tokenGenerator.generateToken(userDetails, tokenConfiguration.getRefreshTokenExpiration());
            AccessRefreshToken accessRefreshToken = tokenRenderer.render(userDetails, tokenConfiguration.getDefaultExpiration(), accessToken, refreshToken);
            return httpResponseWithAccessRefreshToken(accessRefreshToken);
        } catch (Exception e) {
            return HttpResponse.serverError();
        }
    }

    /**
     *
     * @param refreshToken The refresh token - a JWT token
     * @param claims The claims to generate the access token
     * @return An AccessRefreshToken encapsulated in the HttpResponse or a failure indicated by the HTTP status
     */
    @Override
    public HttpResponse<AccessRefreshToken> generate(String refreshToken, Map<String, Object> claims) {
        claims.put(JwtClaims.EXPIRATION_TIME, expirationDate());
        try {
            String accessToken = tokenGenerator.generateToken(claims);
            AccessRefreshToken accessRefreshToken = tokenRenderer.render(tokenConfiguration.getDefaultExpiration(), accessToken, refreshToken);
            return httpResponseWithAccessRefreshToken(accessRefreshToken);
        } catch (Exception e) {
            return HttpResponse.serverError();
        }
    }

    /**
     * An expiration Date built with current date + default expiration.
     * @return java.util.Date
     */
    protected Date expirationDate() {
        Integer expiration = tokenConfiguration.getDefaultExpiration();
        Date now = new Date();
        LOG.debug("Setting expiration to {}", expiration.toString());
        return new Date(now.getTime() + (expiration * MILLISECONDS_IN_A_SECOND));
    }
}
